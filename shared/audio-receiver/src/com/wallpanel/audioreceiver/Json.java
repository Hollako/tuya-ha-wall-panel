package com.wallpanel.audioreceiver;

import java.util.*;

public class Json {
  public static String escape(String s) {
    if (s == null) return "null";
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"': sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '\b': sb.append("\\b"); break;
        case '\f': sb.append("\\f"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        default:
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
      }
    }
    return sb.toString();
  }

  public static String object(Map<String, Object> m) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> e : m.entrySet()) {
      if (!first) sb.append(",");
      first = false;
      sb.append("\"").append(escape(e.getKey())).append("\":");
      sb.append(value(e.getValue()));
    }
    sb.append("}");
    return sb.toString();
  }

  public static String value(Object v) {
    if (v == null) return "null";
    if (v instanceof String) return "\"" + escape((String) v) + "\"";
    if (v instanceof Number || v instanceof Boolean) return v.toString();
    if (v instanceof Map) return object((Map<String, Object>) v);
    if (v instanceof List) {
      StringBuilder sb = new StringBuilder("[");
      boolean first = true;
      for (Object item : (List<?>) v) {
        if (!first) sb.append(",");
        first = false;
        sb.append(value(item));
      }
      sb.append("]");
      return sb.toString();
    }
    return "\"" + escape(v.toString()) + "\"";
  }
}
