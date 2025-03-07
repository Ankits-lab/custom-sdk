/**
 * Copyright (C) 2016 The CyanogenMod Project
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

package org.lineageos.platform.internal;

import android.content.Context;
import com.android.server.SystemService;

public abstract class LineageSystemService extends SystemService {
    public LineageSystemService(Context context) {
        super(context);
    }

    public abstract String getFeatureDeclaration();


    /**
     * Override and return true if the service should be started
     * before the device is decrypted.
     */
    public boolean isCoreService() {
        return true;
    }
}
