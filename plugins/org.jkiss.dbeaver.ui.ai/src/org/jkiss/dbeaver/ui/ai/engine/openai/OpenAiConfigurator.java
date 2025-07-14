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
package org.jkiss.dbeaver.ui.ai.engine.openai;

import com.theokanning.openai.model.Model;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Text;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.ai.engine.AIEngine;
import org.jkiss.dbeaver.model.ai.engine.LegacyAISettings;
import org.jkiss.dbeaver.model.ai.engine.openai.OpenAIClient;
import org.jkiss.dbeaver.model.ai.engine.openai.OpenAIModels;
import org.jkiss.dbeaver.model.ai.engine.openai.OpenAIProperties;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.IObjectPropertyConfigurator;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.ai.FieldValidationException;
import org.jkiss.dbeaver.ui.ai.internal.AIUIMessages;
import org.jkiss.utils.CommonUtils;

import java.util.List;
import java.util.Locale;

public class OpenAiConfigurator implements IObjectPropertyConfigurator<AIEngine, LegacyAISettings<OpenAIProperties>> {
    private static final String API_KEY_URL = "https://platform.openai.com/account/api-keys";
    private String token = "";
    private String temperature = "0.0";
    private boolean logQuery = false;

    @Nullable
    protected Text tokenText;
    private Text temperatureText;
    private ModelSelectorField modelSelectorField;
    private ContextWindowSizeField contextWindowSizeField;
    private Button logQueryCheck;

    @Override
    public void createControl(
        @NotNull Composite parent,
        AIEngine object,
        @NotNull Runnable propertyChangeListener
    ) {
        Composite composite = UIUtils.createComposite(parent, 3);
        composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        createConnectionParameters(composite);

        createModelParameters(composite);

        createAdditionalSettings(composite);
        UIUtils.syncExec(this::applySettings);
    }

    @Override
    public void loadSettings(@NotNull LegacyAISettings<OpenAIProperties> configuration) {
        token = CommonUtils.toString(configuration.getProperties().getToken());
        modelSelectorField.setSelectedModel(
            CommonUtils.toString(configuration.getProperties().getModel(), OpenAIModels.DEFAULT_MODEL)
        );
        temperature = CommonUtils.toString(configuration.getProperties().getTemperature(), "0.0");
        logQuery = CommonUtils.toBoolean(configuration.getProperties().isLoggingEnabled());
        applySettings();

        contextWindowSizeField.setValue(configuration.getProperties().getContextWindowSize());
    }

    @Override
    public void saveSettings(@NotNull LegacyAISettings<OpenAIProperties> configuration) {
        try {
            configuration.getProperties().setToken(token);
            configuration.getProperties().setModel(modelSelectorField.getSelectedModel());
            configuration.getProperties().setContextWindowSize(contextWindowSizeField.getValue());
            configuration.getProperties().setTemperature(Double.parseDouble(temperature));
            configuration.getProperties().setLoggingEnabled(logQuery);
        } catch (FieldValidationException e) {
            DBWorkbench.getPlatformUI().showError(
                "Invalid settings",
                "Failed to save OpenAI settings: " + e.getMessage(),
                e
            );
        }
    }

    @Override
    public void resetSettings(@NotNull LegacyAISettings<OpenAIProperties> openAIPropertiesLegacyAISettings) {

    }

    protected void createAdditionalSettings(@NotNull Composite parent) {
        logQueryCheck = UIUtils.createCheckbox(
            parent,
            "Write GPT queries to debug log",
            "Write GPT queries with metadata info in debug logs",
            false,
            2
        );
        logQueryCheck.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                logQuery = logQueryCheck.getSelection();
            }
        });
    }

    protected void createModelParameters(@NotNull Composite parent) {
        modelSelectorField = ModelSelectorField.builder()
            .withParent(parent)
            .withModelListSupplier(
                this::fetchModels
            )
            .withSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
                try {
                    contextWindowSizeField.setValue(OpenAIModels.getContextWindowSize(modelSelectorField.getSelectedModel()));
                } catch (FieldValidationException ex) {
                    // If the model is not recognized, we can set a default value or handle it accordingly
                }
            }))
            .build();

        contextWindowSizeField = ContextWindowSizeField.builder()
            .withParent(parent)
            .withGridData(GridDataFactory.fillDefaults().span(2, 1).create())
            .build();

        temperatureText = UIUtils.createLabelText(parent, AIUIMessages.gpt_preference_page_text_temperature, "0.0");
        temperatureText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
        temperatureText.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());

        UIUtils.createInfoLabel(parent, "Lower temperatures give more precise results", GridData.FILL_HORIZONTAL, 3);
        temperatureText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
        temperatureText.addModifyListener((e) -> temperature = temperatureText.getText());
    }

    @NotNull
    private List<String> fetchModels() {
        if (token == null || token.isEmpty()) {
            return List.of();
        }

        try (var client = OpenAIClient.createClient(token)) {
            return UIUtils.runWithMonitor(monitor -> {
                var models = client.getModels(monitor);
                return models.stream()
                    .map(Model::getId)
                    .filter(OpenAIModels::isTextModel)
                    .sorted(String::compareToIgnoreCase)
                    .toList();
            });
        } catch (DBException e) {
            DBWorkbench.getPlatformUI().showError(
                "Failed to load GPT models",
                "Could not fetch models from OpenAI: " + e.getMessage(),
                e
            );
            return List.of();
        }
    }

    protected void createConnectionParameters(@NotNull Composite parent) {
        tokenText = UIUtils.createLabelText(
            parent,
            AIUIMessages.gpt_preference_page_selector_token,
            "",
            SWT.BORDER | SWT.PASSWORD
        );
        GridData layoutData = new GridData(GridData.FILL_HORIZONTAL);
        layoutData.widthHint = 100;
        tokenText.setLayoutData(layoutData);
        tokenText.addModifyListener((e -> token = tokenText.getText()));
        tokenText.setMessage("API access token");
        createURLInfoLink(parent);
    }

    protected void createURLInfoLink(@NotNull Composite parent) {
        Link link = UIUtils.createLink(
            parent,
            NLS.bind(AIUIMessages.gpt_preference_page_token_info, getApiKeyURL()),
            new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    UIUtils.openWebBrowser(getApiKeyURL());
                }
            }
        );
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 3;
        link.setLayoutData(gd);
    }

    protected String getApiKeyURL() {
        return API_KEY_URL;
    }

    protected void applySettings() {
        if (tokenText != null) {
            tokenText.setText(token);
        }

        temperatureText.setText(temperature);
        logQueryCheck.setSelection(logQuery);
    }

    @Override
    public boolean isComplete() {
        return tokenText != null
            && !tokenText.getText().isEmpty()
            && contextWindowSizeField.isComplete();
    }
}
