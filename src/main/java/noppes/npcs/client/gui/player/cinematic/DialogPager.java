package noppes.npcs.client.gui.player.cinematic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a dialog text into pages of at most maxLines lines, cutting at the end of a
 * paragraph or a sentence whenever it can. Authors use blank lines as paragraph spacing,
 * so a paragraph starts on a new line but shares the page with the previous one if it
 * fits; a single line break is kept as a line break.
 *
 * Pause marks of the gradual text ({20}) arrive here already turned into {@link #PAUSE}
 * characters, which take no width.
 */
public final class DialogPager {
    public static final char PAUSE = '\u0007';

    private static final Pattern SENTENCE = Pattern.compile("[^.!?]+[.!?]+[\"')»]*\\s*|[^.!?]+$");

    private DialogPager() {
    }

    public static int width(String s) {
        return CinematicDraw.width(s.replace(String.valueOf(PAUSE), ""));
    }

    /**
     * @return one list of lines per page, never empty
     */
    public static List<List<String>> paginate(String text, int wrapW, int maxLines) {
        List<String> pages = new ArrayList<String>();
        String cur = "";
        // \r alone is an old line break that many dialogs still use ("\r\r" between paragraphs)
        String normal = text.replace("\r\n", "\n").replace('\r', '\n');
        for (String para : normal.split("\n\\s*\n")) {
            boolean firstOfParagraph = true;
            for (String raw : sentences(para)) {
                String sen = raw.trim();
                if (sen.isEmpty())
                    continue;
                String joined = cur.isEmpty() ? sen : cur + (firstOfParagraph ? "\n" : " ") + sen;
                firstOfParagraph = false;
                if (wrap(joined, wrapW).size() <= maxLines) {
                    cur = joined;
                    continue;
                }
                if (!cur.isEmpty())
                    pages.add(cur);
                List<String> lines = wrap(sen, wrapW);
                if (lines.size() <= maxLines) {
                    cur = sen;
                    continue;
                }
                // A single sentence does not fit: cut it by lines
                cur = "";
                for (int i = 0; i < lines.size(); i += maxLines) {
                    String chunk = join(lines.subList(i, Math.min(lines.size(), i + maxLines)));
                    if (i + maxLines < lines.size())
                        pages.add(chunk);
                    else
                        cur = chunk;
                }
            }
        }
        if (!cur.isEmpty())
            pages.add(cur);
        List<List<String>> out = new ArrayList<List<String>>();
        String carry = "";
        for (String page : pages) {
            // a color opened on one page goes on in the next
            out.add(wrap(carry + page, wrapW));
            carry = formatAtEnd(carry + page);
        }
        if (out.isEmpty()) {
            List<String> empty = new ArrayList<String>();
            empty.add("");
            out.add(empty);
        }
        return out;
    }

    private static List<String> sentences(String para) {
        List<String> out = new ArrayList<String>();
        Matcher m = SENTENCE.matcher(para);
        while (m.find())
            out.add(m.group());
        if (out.isEmpty())
            out.add(para);
        return out;
    }

    private static String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (sb.length() > 0)
                sb.append(' ');
            // the color carried at the start of a wrapped line is added again by wrap()
            sb.append(l);
        }
        return sb.toString();
    }

    /**
     * Word wrap that keeps line breaks and carries the active color code to the next line.
     */
    public static List<String> wrap(String text, int maxW) {
        List<String> out = new ArrayList<String>();
        String carry = "";
        for (String hard : text.split("\n", -1)) {
            String line = "";
            for (String word : hard.trim().split(" ")) {
                if (word.isEmpty())
                    continue;
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(carry + candidate) > maxW && !line.isEmpty()) {
                    out.add(carry + line);
                    carry = formatAtEnd(carry + line);
                    line = word;
                } else {
                    line = candidate;
                }
            }
            out.add(carry + line);
            carry = formatAtEnd(carry + line);
        }
        return out;
    }

    /**
     * Color and style codes still active at the end of s, to start the next line with
     * them (vanilla has the same helper, but private).
     */
    static String formatAtEnd(String s) {
        String color = "";
        StringBuilder styles = new StringBuilder();
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) != '§')
                continue;
            char k = Character.toLowerCase(s.charAt(i + 1));
            if ("0123456789abcdef".indexOf(k) >= 0) {
                color = "§" + k;
                styles.setLength(0);
            } else if ("klmno".indexOf(k) >= 0) {
                styles.append('§').append(k);
            } else if (k == 'r') {
                color = "";
                styles.setLength(0);
            }
            i++;
        }
        return color + styles;
    }

    /**
     * Number of visible characters: color codes and pause marks do not count.
     */
    public static int visibleLength(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (c != PAUSE)
                n++;
        }
        return n;
    }

    /**
     * Prefix of s holding the first {@code visible} visible characters, color codes included.
     */
    public static String prefix(String s, int visible) {
        int n = 0;
        int i = 0;
        for (; i < s.length() && n < visible; i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (c != PAUSE)
                n++;
        }
        return s.substring(0, Math.min(i, s.length()));
    }

    public static String strip(String s) {
        return s.replace(String.valueOf(PAUSE), "");
    }
}
