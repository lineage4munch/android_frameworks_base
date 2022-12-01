/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "optimize/Obfuscator.h"

#include <map>
#include <set>
#include <string>
#include <unordered_set>

#include "ResourceTable.h"
#include "ValueVisitor.h"
#include "androidfw/StringPiece.h"
#include "util/Util.h"

static const char base64_chars[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    "abcdefghijklmnopqrstuvwxyz"
    "0123456789-_";

namespace aapt {

Obfuscator::Obfuscator(OptimizeOptions& optimizeOptions)
    : options_(optimizeOptions.table_flattener_options),
      shorten_resource_paths_(optimizeOptions.shorten_resource_paths),
      collapse_key_stringpool_(optimizeOptions.table_flattener_options.collapse_key_stringpool) {
}

std::string ShortenFileName(android::StringPiece file_path, int output_length) {
  std::size_t hash_num = std::hash<android::StringPiece>{}(file_path);
  std::string result = "";
  // Convert to (modified) base64 so that it is a proper file path.
  for (int i = 0; i < output_length; i++) {
    uint8_t sextet = hash_num & 0x3f;
    hash_num >>= 6;
    result += base64_chars[sextet];
  }
  return result;
}

// Return the optimal hash length such that at most 10% of resources collide in
// their shortened path.
// Reference: http://matt.might.net/articles/counting-hash-collisions/
int OptimalShortenedLength(int num_resources) {
  if (num_resources > 4000) {
    return 3;
  } else {
    return 2;
  }
}

std::string GetShortenedPath(android::StringPiece shortened_filename,
                             android::StringPiece extension, int collision_count) {
  std::string shortened_path = std::string("res/") += shortened_filename;
  if (collision_count > 0) {
    shortened_path += std::to_string(collision_count);
  }
  shortened_path += extension;
  return shortened_path;
}

// implement custom comparator of FileReference pointers so as to use the
// underlying filepath as key rather than the integer address. This is to ensure
// determinism of output for colliding files.
struct PathComparator {
  bool operator()(const FileReference* lhs, const FileReference* rhs) const {
    return lhs->path->compare(*rhs->path);
  }
};

static bool HandleShortenFilePaths(ResourceTable* table,
                                   std::map<std::string, std::string>& shortened_path_map) {
  // used to detect collisions
  std::unordered_set<std::string> shortened_paths;
  std::set<FileReference*, PathComparator> file_refs;
  for (auto& package : table->packages) {
    for (auto& type : package->types) {
      for (auto& entry : type->entries) {
        for (auto& config_value : entry->values) {
          FileReference* file_ref = ValueCast<FileReference>(config_value->value.get());
          if (file_ref) {
            file_refs.insert(file_ref);
          }
        }
      }
    }
  }
  int num_chars = OptimalShortenedLength(file_refs.size());
  for (auto& file_ref : file_refs) {
    android::StringPiece res_subdir, actual_filename, extension;
    util::ExtractResFilePathParts(*file_ref->path, &res_subdir, &actual_filename, &extension);

    // Android detects ColorStateLists via pathname, skip res/color*
    if (util::StartsWith(res_subdir, "res/color")) continue;

    std::string shortened_filename = ShortenFileName(*file_ref->path, num_chars);
    int collision_count = 0;
    std::string shortened_path = GetShortenedPath(shortened_filename, extension, collision_count);
    while (shortened_paths.find(shortened_path) != shortened_paths.end()) {
      collision_count++;
      shortened_path = GetShortenedPath(shortened_filename, extension, collision_count);
    }
    shortened_paths.insert(shortened_path);
    shortened_path_map.insert({*file_ref->path, shortened_path});
    file_ref->path = table->string_pool.MakeRef(shortened_path, file_ref->path.GetContext());
  }
  return true;
}

void Obfuscator::ObfuscateResourceName(
    const bool collapse_key_stringpool, const std::set<ResourceName>& name_collapse_exemptions,
    const ResourceNamedType& type_name, const ResourceTableEntryView& entry,
    const android::base::function_ref<void(Result obfuscatedResult, const ResourceName&)>
        onObfuscate) {
  ResourceName resource_name({}, type_name, entry.name);
  if (!collapse_key_stringpool ||
      name_collapse_exemptions.find(resource_name) != name_collapse_exemptions.end()) {
    onObfuscate(Result::Keep_ExemptionList, resource_name);
  } else {
    // resource isn't exempt from collapse, add it as obfuscated value
    if (entry.overlayable_item) {
      // if the resource name of the specific entry is obfuscated and this
      // entry is in the overlayable list, the overlay can't work on this
      // overlayable at runtime because the name has been obfuscated in
      // resources.arsc during flatten operation.
      onObfuscate(Result::Keep_Overlayable, resource_name);
    } else {
      onObfuscate(Result::Obfuscated, resource_name);
    }
  }
}

static bool HandleCollapseKeyStringPool(
    const ResourceTable* table, const bool collapse_key_string_pool,
    const std::set<ResourceName>& name_collapse_exemptions,
    std::unordered_map<uint32_t, std::string>& id_resource_map) {
  if (!collapse_key_string_pool) {
    return true;
  }

  int entryResId = 0;
  auto onObfuscate = [&entryResId, &id_resource_map](const Obfuscator::Result obfuscatedResult,
                                                     const ResourceName& resource_name) {
    if (obfuscatedResult == Obfuscator::Result::Obfuscated) {
      id_resource_map.insert({entryResId, resource_name.entry});
    }
  };

  for (auto& package : table->packages) {
    for (auto& type : package->types) {
      for (auto& entry : type->entries) {
        if (!entry->id.has_value() || entry->name.empty()) {
          continue;
        }
        entryResId = entry->id->id;
        ResourceTableEntryView entry_view{
            .name = entry->name,
            .id = entry->id ? entry->id.value().entry_id() : (std::optional<uint16_t>)std::nullopt,
            .visibility = entry->visibility,
            .allow_new = entry->allow_new,
            .overlayable_item = entry->overlayable_item,
            .staged_id = entry->staged_id};

        Obfuscator::ObfuscateResourceName(collapse_key_string_pool, name_collapse_exemptions,
                                          type->named_type, entry_view, onObfuscate);
      }
    }
  }

  return true;
}

bool Obfuscator::Consume(IAaptContext* context, ResourceTable* table) {
  HandleCollapseKeyStringPool(table, options_.collapse_key_stringpool,
                              options_.name_collapse_exemptions, options_.id_resource_map);
  if (shorten_resource_paths_) {
    return HandleShortenFilePaths(table, options_.shortened_path_map);
  }
  return true;
}

/**
 * Tell the optimizer whether it's needed to dump information for de-obfuscating.
 *
 * There are two conditions need to dump the information for de-obfuscating.
 * * the option of shortening file paths is enabled.
 * * the option of collapsing resource names is enabled.
 * @return true if the information needed for de-obfuscating, otherwise false
 */
bool Obfuscator::IsEnabled() const {
  return shorten_resource_paths_ || collapse_key_stringpool_;
}

}  // namespace aapt
