/*
 *  Copyright (C) <2024> <XiaoMoMi>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.momirealms.customnameplates.api.feature.advance;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface CharacterFontAdvanceData {

    int size();

    Float getAdvance(int codePoint);

    Map<Integer, Float> data();

    String id();

    List<JsonObject> fontProvider(Map<String, Object> properties);

    static Builder builder() {
        return new CharacterFontAdvanceDataImpl.BuilderImpl();
    }

    void close();

    interface Builder {

        Builder advance(Map<Integer, Float> data);

        Builder id(String id);

        Builder fontProviderFunction(Function<Map<String, Object>, List<JsonObject>> function);

        CharacterFontAdvanceData build();
    }
}