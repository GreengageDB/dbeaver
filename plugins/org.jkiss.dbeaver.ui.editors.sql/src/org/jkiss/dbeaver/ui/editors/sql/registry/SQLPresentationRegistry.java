/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IEvaluationService;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.preferences.DBPPreferenceStore;
import org.jkiss.dbeaver.runtime.DBWorkbench;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class SQLPresentationRegistry  {
    private static final Log log = Log.getLog(SQLPresentationRegistry.class);

    static final String TAG_PRESENTATION = "presentation"; //$NON-NLS-1$

    private static SQLPresentationRegistry instance = null;

    private List<SQLPresentationDescriptor> presentations = new ArrayList<>();
    private final List<ISQLPresentationContributor> toggleablePresentations = new ArrayList<>();

    public static synchronized SQLPresentationRegistry getInstance()
    {
        if (instance == null) {
            instance = new SQLPresentationRegistry();
            instance.loadExtensions(Platform.getExtensionRegistry());
        }
        return instance;
    }

    private SQLPresentationRegistry()
    {
    }

    private void loadExtensions(IExtensionRegistry registry) {
        DBPPreferenceStore prefs = DBWorkbench.getPlatform().getPreferenceStore();

        for (IConfigurationElement ext : registry.getConfigurationElementsFor(SQLPresentationDescriptor.EXTENSION_ID)) {
            if (TAG_PRESENTATION.equals(ext.getName())) {
                final String id = ext.getAttribute("id");

                IConfigurationElement[] configElements = ext.getChildren(ISQLPresentationContributor.TAG_ENABLEMENT);
                if (configElements.length > 0) {
                    IConfigurationElement config = configElements[0];
                    String settingKey = config.getAttribute(ISQLPresentationContributor.ATTR_SETTING_KEY);
                    String label = config.getAttribute(ISQLPresentationContributor.ATTR_LABEL);
                    String tooltip = config.getAttribute(ISQLPresentationContributor.ATTR_TOOLTIP);

                    registerPresentationWithEnablement(id, settingKey, label, tooltip, ext, prefs);
                } else {
                    presentations.add(new SQLPresentationDescriptor(ext));
                }
            }
        }
        sortPresentations();
    }

    private void registerPresentationWithEnablement(
        @NotNull String id,
        @Nullable String settingKey,
        @Nullable String label,
        @Nullable String tooltip,
        @NotNull IConfigurationElement ext,
        @NotNull DBPPreferenceStore prefs
    ) {
        if (settingKey != null && label != null && tooltip != null) {
            ISQLPresentationContributor contributor = createContributor(
                id,
                settingKey,
                label,
                tooltip,
                ext.getContributor().getName(),
                this::togglePresentation
            );
            toggleablePresentations.add(contributor);
            if (prefs.getBoolean(settingKey)) {
                presentations.add(new SQLPresentationDescriptor(ext));
            }
        } else {
            log.warn("Presentation '" + id + "' has incomplete enablement config");
            presentations.add(new SQLPresentationDescriptor(ext));
        }
    }

    private void sortPresentations() {
        presentations.sort(Comparator
            .comparingInt(SQLPresentationDescriptor::getOrder)
            .thenComparing(SQLPresentationDescriptor::getLabel));
    }

    public void dispose()
    {
        presentations.clear();
        toggleablePresentations.clear();
    }

    public List<SQLPresentationDescriptor> getPresentations() {
        return new ArrayList<>(presentations);
    }

    @Nullable
    public SQLPresentationDescriptor getPresentation(@NotNull String id) {
        for (SQLPresentationDescriptor presentation : presentations) {
            if (presentation.getId().equals(id)) {
                return presentation;
            }
        }

        return null;
    }

    public List<ISQLPresentationContributor> getToggleablePresentations() {
        return new ArrayList<>(toggleablePresentations);
    }

    public void togglePresentation(@NotNull String id, boolean enabled) {

        boolean alreadyPresent = presentations.stream()
            .anyMatch(p -> id.equals(p.getId()));

        if (enabled) {
            if (alreadyPresent) {
                return;
            }
            IExtensionRegistry extensionRegistry = Platform.getExtensionRegistry();
            IConfigurationElement[] elements = extensionRegistry.getConfigurationElementsFor(SQLPresentationDescriptor.EXTENSION_ID);

            for (IConfigurationElement element : elements) {
                if (TAG_PRESENTATION.equals(element.getName()) && id.equals(element.getAttribute("id"))) {
                    SQLPresentationDescriptor descriptor = new SQLPresentationDescriptor(element);
                    this.presentations.add(descriptor);
                    sortPresentations();
                    updateUI(id);
                    break;
                }
            }
        } else {
            if (alreadyPresent) {
                presentations = presentations.stream()
                    .filter(p -> !id.equals(p.getId()))
                    .collect(Collectors.toList());
                updateUI(id);
            }
        }
    }

    private void updateUI(String id) {
        toggleablePresentations.stream()
            .filter(c -> id.equals(c.getId()))
            .findFirst()
            .ifPresent(c ->
                PlatformUI.getWorkbench()
                    .getService(IEvaluationService.class)
                    .requestEvaluation(c.getSettingKey())
            );

    }

    @NotNull
    private static ISQLPresentationContributor createContributor(
        @NotNull String id,
        @NotNull String settingKey,
        @NotNull String label,
        @NotNull String tooltip,
        @NotNull String bandleName,
        @NotNull BiConsumer<String, Boolean> toggler) {

        return new ISQLPresentationContributor() {

            @Override
            public String getBundleName() {
                return bandleName;
            }

            @Override
            public String getSettingKey() {
                return settingKey;
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public String getTooltip() {
                return tooltip;
            }

            @Override
            public void togglePresentation(String id, boolean state) {
                toggler.accept(id, state);
            }
        };
    }

}
