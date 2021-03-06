/*
 * Copyright 2013 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import okio.ByteString;

import static com.squareup.wire.Message.Datatype;
import static com.squareup.wire.Message.Label;
import static java.util.Collections.unmodifiableMap;

class MessageTypeAdapter<M extends Message> extends TypeAdapter<M> {

  enum UnknownFieldType {
    VARINT, FIXED32, FIXED64, LENGTH_DELIMITED;

    public static UnknownFieldType of(String name) {
      if ("varint".equals(name)) return VARINT;
      if ("fixed32".equals(name)) return FIXED32;
      if ("fixed64".equals(name)) return FIXED64;
      if ("length-delimited".equals(name)) return LENGTH_DELIMITED;
      throw new IllegalArgumentException("Unknown type " + name);
    }
  }

  // 2^64, used to convert sint64 values >= 2^63 to unsigned decimal form
  private static final BigInteger POWER_64 = new BigInteger("18446744073709551616");

  private final Gson gson;
  private final RuntimeMessageAdapter<M> messageAdapter;
  private final Map<String, TagBinding<M, Message.Builder<M>>> tagMap;

  @SuppressWarnings("unchecked")
  public MessageTypeAdapter(Wire wire, Gson gson, TypeToken<M> type) {
    this.gson = gson;
    this.messageAdapter = wire.messageAdapter((Class<M>) type.getRawType());

    Map<String, TagBinding<M, Message.Builder<M>>> tagMap
        = new LinkedHashMap<String, TagBinding<M, Message.Builder<M>>>();
    for (TagBinding<M, Message.Builder<M>> tagBinding : messageAdapter.tagBindings().values()) {
      tagMap.put(tagBinding.name, tagBinding);
    }
    this.tagMap = unmodifiableMap(tagMap);
  }

  @SuppressWarnings("unchecked")
  @Override public void write(JsonWriter out, M message) throws IOException {
    if (message == null) {
      out.nullValue();
      return;
    }

    out.beginObject();
    for (TagBinding<M, Message.Builder<M>> tagBinding
        : messageAdapter.tagBindingsForMessage(message).values()) {
      Object value = tagBinding.get(message);
      if (value == null) {
        continue;
      }
      out.name(tagBinding.name);
      emitJson(out, value, tagBinding.datatype, tagBinding.label);
    }

    Collection<List<UnknownFieldMap.Value>> unknownFields = message.unknownFields();
    if (unknownFields != null) {
      for (List<UnknownFieldMap.Value> fieldList : unknownFields) {
        int tag = fieldList.get(0).tag;
        out.name(String.valueOf(tag));
        out.beginArray();
        for (int i = 0, count = fieldList.size(); i < count; i++) {
          UnknownFieldMap.Value unknownField = fieldList.get(i);
          switch (unknownField.adapter.fieldEncoding) {
            case VARINT:
              if (i == 0) out.value("varint");
              out.value((Long) unknownField.value);
              break;
            case FIXED32:
              if (i == 0) out.value("fixed32");
              out.value((Integer) unknownField.value);
              break;
            case FIXED64:
              if (i == 0) out.value("fixed64");
              out.value((Long) unknownField.value);
              break;
            case LENGTH_DELIMITED:
              if (i == 0) out.value("length-delimited");
              out.value(((ByteString) unknownField.value).base64());
              break;
            default:
              throw new AssertionError("Unknown wire type " + unknownField.adapter.fieldEncoding);
          }
        }
        out.endArray();
      }
    }

    out.endObject();
  }

  @SuppressWarnings("unchecked")
  private void emitJson(JsonWriter out, Object value, Datatype datatype, Label label)
      throws IOException {
    if (datatype == Datatype.UINT64) {
      if (label.isRepeated()) {
        List<Long> longs = (List<Long>) value;
        out.beginArray();
        for (int i = 0, count = longs.size(); i < count; i++) {
          emitUint64(longs.get(i), out);
        }
        out.endArray();
      } else {
        emitUint64((Long) value, out);
      }
    } else {
      gson.toJson(value, value.getClass(), out);
    }
  }

  private void emitUint64(Long value, JsonWriter out) throws IOException {
    if (value < 0) {
      BigInteger unsigned = POWER_64.add(BigInteger.valueOf(value));
      out.value(unsigned);
    } else {
      out.value(value);
    }
  }

  @SuppressWarnings("unchecked")
  @Override public M read(JsonReader in) throws IOException {
    if (in.peek() == JsonToken.NULL) {
      in.nextNull();
      return null;
    }

    Message.Builder<M> builder = messageAdapter.newBuilder();
    in.beginObject();

    while (in.peek() == JsonToken.NAME) {
      String name = in.nextName();
      TagBinding<M, Message.Builder<M>> tagBinding = tagMap.get(name);

      if (tagBinding != null) {
        Object value = parseValue(tagBinding.label, singleType(tagBinding), parse(in));
        tagBinding.set(builder, value);
      } else {
        parseUnknownField(in, builder, Integer.parseInt(name));
      }
    }

    in.endObject();
    return builder.build();
  }

  private JsonElement parse(JsonReader in) {
    return gson.fromJson(in, JsonElement.class);
  }

  private Object parseValue(Label label, Type valueType, JsonElement valueElement) {
    if (label.isRepeated()) {
      List<Object> valueList = new ArrayList<Object>();
      for (JsonElement element : valueElement.getAsJsonArray()) {
        valueList.add(readJson(valueType, element));
      }
      return valueList;
    } else {
      return readJson(valueType, valueElement);
    }
  }

  private void parseUnknownField(JsonReader in, Message.Builder<M> builder, int tag)
      throws IOException {
    in.beginArray();
    UnknownFieldType type = UnknownFieldType.of(in.nextString());
    while (in.peek() != JsonToken.END_ARRAY) {
      switch (type) {
        case VARINT:
          builder.addVarint(tag, in.nextInt());
          break;
        case FIXED32:
          builder.addFixed32(tag, in.nextInt());
          break;
        case FIXED64:
          builder.addFixed64(tag, in.nextInt());
          break;
        case LENGTH_DELIMITED:
          builder.addLengthDelimited(tag, ByteString.decodeBase64(in.nextString()));
          break;
        default:
          throw new AssertionError("Unknown field type " + type);
      }
    }
    in.endArray();
  }

  private Object readJson(Type valueType, JsonElement element) {
    return gson.fromJson(element, valueType);
  }

  private Type singleType(TagBinding<M, Message.Builder<M>> tagBinding) {
    return tagBinding.singleAdapter.javaType;
  }
}
