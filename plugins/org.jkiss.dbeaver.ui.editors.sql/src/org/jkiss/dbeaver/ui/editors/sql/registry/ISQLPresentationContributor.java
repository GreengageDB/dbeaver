/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ui.editors.sql.registry;

import java.util.function.BiConsumer;

/**
 * Represents a contributor to the SQL editor presentation system.
 *
 * <p>This interface is used to describe optional editor presentations (e.g. Visual Query Builder)
 * that can be enabled or disabled via preferences. Implementations of this interface provide metadata
 * for the UI (labels, tooltips), preference integration (setting key), and dynamic switching logic.</p>
 *
 * <p>These contributors are typically declared via extension points in plugin.xml and consumed
 * by {@link SQLPresentationRegistry} to build the presentation list and the preferences page.</p>
 */
public interface ISQLPresentationContributor {

    /**
     * Tag name for the XML element defining presentation enablement configuration.
     * Used in plugin.xml to specify additional metadata for SQL presentations.
     */
    String TAG_ENABLEMENT = "enablement";

    /**
     * Attribute key used in {@code <enablement>} tag to store the preference key
     * controlling whether this presentation is enabled.
     */
    String ATTR_SETTING_KEY = "setting-key";

    /**
     * Attribute key used in {@code <enablement>} tag to define the user-visible label
     * for this presentation in the preferences UI.
     */
    String ATTR_LABEL = "label";

    /**
     * Attribute key used in {@code <enablement>} tag to provide a tooltip
     * for the corresponding checkbox or setting in the UI.
     */
    String ATTR_TOOLTIP = "tooltip";

    /**
     * Returns the symbolic name of the contributing bundle.
     *
     * @return the bundle name (typically from {@code ext.getContributor().getName()})
     */
    String getBundleName();

    /**
     * Returns the preference key used to enable or disable this presentation.
     *
     * @return the preference setting key
     */
    String getSettingKey();

    /**
     * Returns the unique ID of this presentation contributor.
     *
     * @return the presentation ID, matching the value in the {@code id} attribute of the extension
     */
    String getId();

    /**
     * Returns the user-visible label for this presentation (used in the preferences UI).
     *
     * @return the label text
     */
    String getLabel();

    /**
     * Returns the tooltip to be shown in the preferences UI.
     *
     * @return the tooltip text
     */
    String getTooltip();

    /**
     * Enables or disables the presentation with the given ID.
     *
     * @param id    the presentation ID
     * @param state {@code true} to enable, {@code false} to disable
     */
    void togglePresentation(String id, boolean state);
}