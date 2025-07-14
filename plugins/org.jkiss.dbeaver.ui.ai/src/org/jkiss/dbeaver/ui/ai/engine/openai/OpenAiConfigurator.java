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
import org.eclipse.swt.widgets.*;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.model.ai.AIModels;
import org.jkiss.dbeaver.model.ai.engine.AIEngine;
import org.jkiss.dbeaver.model.ai.engine.LegacyAISettings;
import org.jkiss.dbeaver.model.ai.engine.openai.OpenAIClient;
import org.jkiss.dbeaver.model.ai.engine.openai.OpenAIConstants;
import org.jkiss.dbeaver.model.ai.engine.openai.OpenAIProperties;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.IObjectPropertyConfigurator;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.ai.FieldValidationException;
import org.jkiss.dbeaver.ui.ai.internal.AIUIMessages;
import org.jkiss.utils.CommonUtils;

import java.util.Locale;

public class OpenAiConfigurator implements IObjectPropertyConfigurator<AIEngine, LegacyAISettings<OpenAIProperties>> {
    private static final String API_KEY_URL = "https://platform.openai.com/account/api-keys";
    private String token = "";
    private String model = "";
    private String temperature = "0.0";
    private boolean logQuery = false;

    @Nullable
    protected Text tokenText;
    private Text temperatureText;
    private Combo modelCombo;
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
        model = readModel(configuration);
        temperature = CommonUtils.toString(configuration.getProperties().getTemperature(), "0.0");
        logQuery = CommonUtils.toBoolean(configuration.getProperties().isLoggingEnabled());
        applySettings();

        contextWindowSizeField.setValue(configuration.getProperties().getContextWindowSize());

        modelCombo.setItems(model);
        modelCombo.select(0);
    }

    @Override
    public void saveSettings(@NotNull LegacyAISettings<OpenAIProperties> configuration) {
        try {
            configuration.getProperties().setToken(token);
            configuration.getProperties().setModel(model);
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
        modelCombo = UIUtils.createLabelCombo(parent, AIUIMessages.gpt_preference_page_combo_engine, SWT.READ_ONLY);
        modelCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                model = modelCombo.getText();
                contextWindowSizeField.setValue(AIModels.getContextWindowSize(model));
            }
        });

        UIUtils.createDialogButton(
            parent,
            AIUIMessages.gpt_preference_page_refresh_models,
            SelectionListener.widgetSelectedAdapter((e) -> populateModels(true))
        );

        contextWindowSizeField = ContextWindowSizeField.create(
            parent,
            GridDataFactory.fillDefaults().span(2, 1).create()
        );

        temperatureText = UIUtils.createLabelText(parent, AIUIMessages.gpt_preference_page_text_temperature, "0.0");
        temperatureText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
        temperatureText.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).create());

        UIUtils.createInfoLabel(parent, "Lower temperatures give more precise results", GridData.FILL_HORIZONTAL, 3);
        temperatureText.addVerifyListener(UIUtils.getNumberVerifyListener(Locale.getDefault()));
        temperatureText.addModifyListener((e) -> temperature = temperatureText.getText());
    }

    @NotNull
    protected void populateModels(boolean force) {
        if (modelCombo.getItemCount() > 0 && !force) {
            return; // already populated
        }

        if (token == null || token.isEmpty()) {
            return;
        }

        try (var client = OpenAIClient.createClient(token)) {
            String[] modelIds = UIUtils.runWithMonitor(monitor -> {
                var models = client.getModels(monitor);
                return models.stream()
                    .map(Model::getId)
                    .filter(id -> {
                        String idLowerCase = id.toLowerCase(Locale.ROOT);
                        return idLowerCase.startsWith("gpt-") || idLowerCase.startsWith("o");
                    })
                    .sorted(String::compareToIgnoreCase)
                    .toArray(String[]::new);
            });

            modelCombo.setItems(modelIds);

            for (int i = 0; i < modelIds.length; i++) {
                if (model.equals(modelIds[i])) {
                    modelCombo.select(i);
                    contextWindowSizeField.setValue(AIModels.getContextWindowSize(model));
                    break;
                }
            }
        } catch (DBException e) {
            DBWorkbench.getPlatformUI().showError(
                "Failed to load GPT models",
                null,
                e
            );
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

    private String readModel(@NotNull LegacyAISettings<OpenAIProperties> configuration) {
        String savedModel = configuration.getProperties().getModel();
        if (CommonUtils.isEmpty(savedModel)) {
            savedModel = OpenAIConstants.DEFAULT_MODEL;
        }

        return savedModel;
    }

    protected void applySettings() {
        if (tokenText != null) {
            tokenText.setText(token);
        }

        modelCombo.setText(model);
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
