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
package org.jkiss.dbeaver.model.ai.engine.openai;

import com.theokanning.openai.model.Model;
import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;

import java.util.List;
import java.util.Locale;

public class OpenAIModelManager {



    private final OpenAIClient client;

    public OpenAIModelManager(OpenAIClient client) {
        this.client = client;
    }

    public List<String> getAvailableModels(@NotNull DBRProgressMonitor monitor) throws DBCException {
        try {
            return client.getModels(monitor).stream()
                .map(Model::getId)
                .filter(it -> {
                    String idLowerCase = it.toLowerCase(Locale.ROOT);
                    return idLowerCase.startsWith("gpt-") || idLowerCase.startsWith("o");
                })
                .sorted()
                .toList();
        } catch (Exception e) {
            throw new DBCException("Failed to fetch available models", e);
        }
    }



    public record OpenAIModel(
        String name,
        Integer contextWindowSize
    ) {

    }
}
