/*
 * Copyright (C) 2023 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.shadow

import android.content.Context
import android.content.res.TypedArray
import android.testing.AndroidTestingRunner
import android.util.AttributeSet
import androidx.test.filters.SmallTest
import com.android.systemui.R
import com.android.systemui.SysuiTestCase
import com.android.systemui.shared.shadow.DoubleShadowTextClock
import com.android.systemui.shared.shadow.ResourcesProvider
import com.android.systemui.util.mockito.whenever
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidTestingRunner::class)
class DoubleShadowTextClockTest : SysuiTestCase() {
    @get:Rule val mockito: MockitoRule = MockitoJUnit.rule()

    @Mock lateinit var resourcesProvider: ResourcesProvider

    @Mock lateinit var attributes: TypedArray

    private lateinit var context: Context
    private var attrs: AttributeSet? = null

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = getContext()
        whenever(attributes.getBoolean(R.styleable.DoubleShadowTextClock_removeTextDescent, false))
            .thenReturn(true)
    }

    @Test
    fun testAddingPaddingToBottomOfClockWhenConfigIsTrue() {
        whenever(
                resourcesProvider.getBoolean(R.bool.dream_overlay_complication_clock_bottom_padding)
            )
            .thenReturn(true)

        val doubleShadowTextClock =
            DoubleShadowTextClock(
                resourcesProvider = resourcesProvider,
                context = context,
                attrs = attrs,
                attributesInput = attributes
            )
        assertTrue(doubleShadowTextClock.paddingBottom > 0)
    }

    @Test
    fun testRemovingPaddingToBottomOfClockWhenConfigIsFalse() {
        whenever(
                resourcesProvider.getBoolean(R.bool.dream_overlay_complication_clock_bottom_padding)
            )
            .thenReturn(false)

        val doubleShadowTextClock =
            DoubleShadowTextClock(
                resourcesProvider = resourcesProvider,
                context = context,
                attrs = attrs,
                attributesInput = attributes
            )
        assertTrue(doubleShadowTextClock.paddingBottom < 0)
    }
}
