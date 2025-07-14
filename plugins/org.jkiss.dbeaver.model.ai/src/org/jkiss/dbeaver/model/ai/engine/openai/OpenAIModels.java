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

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OpenAIModels {
    private OpenAIModels() {
    }

    public static final String DEFAULT_MODEL = "gpt-4o";
    public static final int DEFAULT_CONTEXT_WINDOW_SIZE = 16_384;

    public static final Map<String, Integer> KNOWN_MODELS = Map.ofEntries(
        Map.entry("o4-mini", 200_000),
        Map.entry("o3-pro", 200_000),
        Map.entry("o3", 200_000),
        Map.entry("o3-mini", 200_000),
        Map.entry("o1-pro", 200_000),
        Map.entry("o1", 200_000),
        Map.entry("o1-mini", 128_000),
        Map.entry("gpt-4.1", 1_048_576),
        Map.entry("gpt-4o", 128_000),
        Map.entry("gpt-4o-mini", 128_000),
        Map.entry("gpt-4-turbo", 128_000),
        Map.entry("gpt-3.5-turbo", 16_384),
        Map.entry("gpt-4", 8_192)
    );

    public static final Set<String> DEPRECATED_MODELS = Set.of(
        "gpt-3.5-turbo-0301",
        "gpt-3.5-turbo-0613",
        "gpt-3.5-turbo-1106",
        "gpt-3.5-turbo-16k",
        "gpt-3.5-turbo-16k-0613",
        "gpt-3.5-turbo-16k-1106"
    );

    @Nullable
    public static Integer getContextWindowSize(@NotNull String modelName) {
        return KNOWN_MODELS.get(modelName);
    }

    /**
     * Returns the replacement model name for the given model name.
     * If the model name is null or empty, returns the default model.
     * If the model name is known, returns it in lowercase.
     * If the model name is deprecated, returns the default model.
     *
     * @param modelName the model name to check
     * @return the replacement model name
     */
    public static String getEffectiveModelName(@Nullable String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return DEFAULT_MODEL;
        }
        String lowerCaseModelName = modelName.toLowerCase(Locale.ROOT);
        if (KNOWN_MODELS.containsKey(lowerCaseModelName)) {
            return lowerCaseModelName;
        }
        if (DEPRECATED_MODELS.contains(lowerCaseModelName)) {
            return DEFAULT_MODEL;
        }
        return lowerCaseModelName;
    }

    /**
     * Checks if the given model name is a text model.
     * A text model is defined as one that starts with "gpt-", "o".
     *
     * @param modelName the model name to check
     * @return true if the model is a text model, false otherwise
     */
    public static boolean isTextModel(@NotNull String modelName) {
        String lowerCaseModelName = modelName.toLowerCase(Locale.ROOT);
        return lowerCaseModelName.startsWith("gpt-")
            || lowerCaseModelName.startsWith("o");
    }
}
