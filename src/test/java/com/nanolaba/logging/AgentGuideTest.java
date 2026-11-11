package com.nanolaba.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure agents rely on in the files generated from {@code agent-guide.src.md}; the content itself is checked
 * by the readme-generator drift check in CI.
 */
class AgentGuideTest {

    @Test
    public void testSkillStartsWithItsFrontMatter() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("skills/nanolog/SKILL.md"), StandardCharsets.UTF_8);

        assertEquals("---", lines.get(0), "the front matter must be the very first line, with no generated header above it");
        assertEquals("name: nanolog", lines.get(1));
        assertTrue(lines.get(2).startsWith("description: Use when "), lines.get(2));
        assertFalse(lines.get(2).substring("description: ".length()).contains(": "),
                    "a \": \" inside an unquoted YAML value breaks the front matter");
        assertEquals("---", lines.get(3));
    }

    @Test
    public void testLlmsTxtStartsWithItsTitleAndSummary() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("llms.txt"), StandardCharsets.UTF_8);

        assertEquals("# Nanolog", lines.get(0));
        assertEquals("", lines.get(1));
        assertTrue(lines.get(2).startsWith("> Nanolog "), "the llms.txt format puts a one-paragraph summary in a quote after the title");
    }
}
