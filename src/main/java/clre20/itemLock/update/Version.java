package clre20.itemLock.update;

/**
 * 版本號比對：去掉開頭的 v，取開頭的數字段（1.2.3-SNAPSHOT → 1, 2, 3）逐段比較，缺的段視為 0。
 */
public final class Version {

    private Version() {}

    public static String strip(String v) {
        if (v == null) {
            return "";
        }
        String s = v.trim();
        if (!s.isEmpty() && (s.charAt(0) == 'v' || s.charAt(0) == 'V')) {
            s = s.substring(1);
        }
        return s;
    }

    public static int compare(String a, String b) {
        int[] x = parts(strip(a));
        int[] y = parts(strip(b));
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    private static int[] parts(String s) {
        int end = 0;
        while (end < s.length() && (Character.isDigit(s.charAt(end)) || s.charAt(end) == '.')) {
            end++;
        }
        String[] seg = s.substring(0, end).split("\\.");
        int[] out = new int[seg.length];
        for (int i = 0; i < seg.length; i++) {
            try {
                out[i] = seg[i].isEmpty() ? 0 : Integer.parseInt(seg[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }
}
