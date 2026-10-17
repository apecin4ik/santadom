package ru.domsanta;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Мини-парсер NBT без зависимостей + вывод в SNBT.
 * Типы: Byte, Short, Integer, Long, Float, Double, String, byte[], int[], long[], List&lt;Object&gt;, Map&lt;String,Object&gt;.
 */
public final class Nbt {

    private Nbt() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readGzip(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))))) {
            int type = in.readUnsignedByte();
            if (type != 10) throw new IOException("Корневой тег NBT должен быть compound");
            in.readUTF(); // имя корня
            return (Map<String, Object>) readPayload(in, 10);
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                return a;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int et = in.readUnsignedByte();
                int n = in.readInt();
                List<Object> l = new ArrayList<>(Math.max(0, n));
                for (int i = 0; i < n; i++) l.add(readPayload(in, et));
                return l;
            }
            case 10: {
                Map<String, Object> m = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) break;
                    String k = in.readUTF();
                    m.put(k, readPayload(in, t));
                }
                return m;
            }
            case 11: {
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default:
                throw new IOException("Неизвестный тип NBT: " + type);
        }
    }

    // ---------------- SNBT ----------------

    public static String toSnbt(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o instanceof Byte b) {
            sb.append(b.intValue()).append('b');
        } else if (o instanceof Short s) {
            sb.append(s.intValue()).append('s');
        } else if (o instanceof Integer i) {
            sb.append(i.intValue());
        } else if (o instanceof Long l) {
            sb.append(l.longValue()).append('L');
        } else if (o instanceof Float f) {
            sb.append(f.floatValue()).append('f');
        } else if (o instanceof Double d) {
            sb.append(d.doubleValue()).append('d');
        } else if (o instanceof String s) {
            quote(sb, s);
        } else if (o instanceof byte[] a) {
            sb.append("[B;");
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(a[i]).append('b');
            }
            sb.append(']');
        } else if (o instanceof int[] a) {
            sb.append("[I;");
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(a[i]);
            }
            sb.append(']');
        } else if (o instanceof long[] a) {
            sb.append("[L;");
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(a[i]).append('L');
            }
            sb.append(']');
        } else if (o instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object e : list) {
                if (!first) sb.append(',');
                first = false;
                write(sb, e);
            }
            sb.append(']');
        } else if (o instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                String k = String.valueOf(e.getKey());
                if (k.matches("[A-Za-z0-9._+-]+")) sb.append(k);
                else quote(sb, k);
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else {
            throw new IllegalArgumentException("Неподдерживаемый тип NBT: " + (o == null ? "null" : o.getClass()));
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
    }
}
