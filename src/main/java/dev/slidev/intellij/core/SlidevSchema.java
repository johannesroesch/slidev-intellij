package dev.slidev.intellij.core;

import java.util.List;

/** Deliberately open schema: themes and addons may define additional keys/values. */
public final class SlidevSchema {
    public record Field(String name, String description, boolean headOnly, List<String> values, String rule) {}
    private static Field field(String name, String description, boolean head, String rule, String... values) {
        return new Field(name, description, head, List.of(values), rule);
    }
    public static final List<Field> FIELDS = List.of(
        field("theme", "Theme package or local path", true, "", "default", "seriph"),
        field("addons", "Addon packages or local paths", true, ""),
        field("title", "Presentation or slide title", false, ""),
        field("titleTemplate", "Browser title template (%s)", true, ""),
        field("author", "Presentation author", true, ""),
        field("info", "Presentation information (Markdown or boolean)", true, ""),
        field("keywords", "Export keywords", true, ""),
        field("layout", "Slide layout (custom layouts are allowed)", false, "", "default", "cover", "center", "intro", "section", "two-cols", "two-cols-header", "image", "image-left", "image-right", "iframe", "iframe-left", "iframe-right", "quote", "statement", "end", "none"),
        field("background", "Background image or color (theme dependent)", false, ""),
        field("class", "Slide CSS classes", false, ""),
        field("transition", "Slide transition name or options", false, "", "slide-left", "slide-right", "slide-up", "slide-down", "fade", "fade-out", "view-transition"),
        field("clicks", "Total click steps", false, "nonnegative"),
        field("clicksStart", "Initial click step", false, "nonnegative"),
        field("hide", "Hide and disable this slide", false, "boolean", "true", "false"),
        field("disabled", "Hide and disable this slide", false, "boolean", "true", "false"),
        field("hideInToc", "Hide this slide in the table of contents", false, "boolean", "true", "false"),
        field("preload", "Preload this slide", false, "boolean", "true", "false"),
        field("level", "Heading level in the table of contents", false, "positive"),
        field("zoom", "Slide zoom factor", false, "positive"),
        field("src", "Imported Markdown file", false, ""),
        field("routeAlias", "Alternative route name", false, ""),
        field("clickAnimation", "Default click animation", false, ""),
        field("dragPos", "Stored draggable element positions", false, ""),
        field("defaults", "Default frontmatter for all slides", true, ""),
        field("fonts", "Font families and provider settings", true, ""),
        field("drawings", "Drawing configuration", true, ""),
        field("export", "Export configuration", true, ""),
        field("download", "Enable download or provide a PDF URL", true, "", "true", "false"),
        field("exportFilename", "Default export filename", true, ""),
        field("presenter", "Enable presenter mode", true, "mode", "true", "false", "dev", "build"),
        field("browserExporter", "Enable browser export", true, "mode", "true", "false", "dev", "build"),
        field("monaco", "Enable Monaco code editor", true, "mode", "true", "false", "dev", "build"),
        field("twoslash", "Enable Twoslash", true, "mode", "true", "false", "dev", "build"),
        field("remoteAssets", "Download remote assets", true, "mode", "true", "false", "dev", "build"),
        field("lineNumbers", "Show code line numbers", true, "boolean", "true", "false"),
        field("codeCopy", "Show code copy button", true, "boolean", "true", "false"),
        field("colorSchema", "Presentation color scheme", true, "enum", "auto", "light", "dark", "all"),
        field("routerMode", "Router mode", true, "enum", "hash", "history", "memory"),
        field("aspectRatio", "Slide aspect ratio, e.g. 16/9", true, ""),
        field("canvasWidth", "Slide canvas width", true, "positive"),
        field("favicon", "Browser icon URL", true, ""),
        field("duration", "Presentation duration, e.g. 30min", true, ""),
        field("timer", "Presentation timer mode", true, "enum", "stopwatch", "countdown")
    );
    public static List<Field> fields(boolean head) {
        return FIELDS.stream().filter(f -> head || !f.headOnly()).toList();
    }
    public static Field find(String name, boolean head) {
        return fields(head).stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }
    public static String scalarProblem(String name, String raw, boolean head) {
        Field field=find(name, head);
        if(field==null || field.rule().isEmpty()) return null;
        String value=raw.strip();
        // YAML anchors, aliases and explicit tags need resolution before type checks.
        if(value.isEmpty() || value.startsWith("&") || value.startsWith("*") || value.startsWith("!")) return null;
        boolean quoted=(value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"));
        String text=quoted ? value.substring(1,value.length()-1) : value;
        return switch(field.rule()) {
            case "boolean" -> !quoted && List.of("true", "false").contains(text.toLowerCase(java.util.Locale.ROOT)) ? null : "Expected true or false";
            case "mode" -> ((!quoted && List.of("true", "false").contains(text.toLowerCase(java.util.Locale.ROOT))) || List.of("dev", "build").contains(text)) ? null : "Expected true, false, dev or build";
            case "enum" -> field.values().contains(text) ? null : "Expected one of: "+String.join(", ",field.values());
            case "nonnegative", "positive" -> {
                try {
                    String numeric=value.replace("_", "");
                    String unsigned=numeric.replaceFirst("^[+-]", "");
                    double number;
                    if(unsigned.startsWith("0x") || unsigned.startsWith("0o")) {
                        number=new java.math.BigInteger(unsigned.substring(2),unsigned.startsWith("0x")?16:8).doubleValue();
                        if(numeric.startsWith("-")) number=-number;
                    } else number=Double.parseDouble(numeric);
                    boolean valid=!quoted && Double.isFinite(number) && (field.rule().equals("positive") ? number>0 : number>=0 && number==Math.rint(number));
                    yield valid ? null : "Expected a "+(field.rule().equals("positive")?"positive number":"nonnegative integer");
                } catch(NumberFormatException ignored) { yield "Expected a "+(field.rule().equals("positive")?"positive number":"nonnegative integer"); }
            }
            default -> null;
        };
    }
    private SlidevSchema() {}
}
