package com.YemomBizGrow.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugGeneratorTest {

    @Test
    @DisplayName("human names become lowercase hyphenated slugs")
    void slugifiesNames() {
        assertThat(SlugGenerator.slugify("Example Digital Agency"))
                .isEqualTo("example-digital-agency");
        assertThat(SlugGenerator.slugify("Addis  Ababa!! Trading"))
                .isEqualTo("addis-ababa-trading");
    }

    @Test
    @DisplayName("diacritics are stripped rather than dropped")
    void stripsDiacritics() {
        assertThat(SlugGenerator.slugify("Café Ñuñoa"))
                .isEqualTo("cafe-nunoa");
    }

    @Test
    @DisplayName("unusable input falls back to the stable default slug")
    void fallsBackForUnusableInput() {
        assertThat(SlugGenerator.slugify(null)).isEqualTo("organization");
        assertThat(SlugGenerator.slugify("!!!")).isEqualTo("organization");
        assertThat(SlugGenerator.slugify("   ")).isEqualTo("organization");
    }

    @Test
    @DisplayName("long names are truncated at 60 characters without a trailing hyphen")
    void truncatesLongInput() {
        String slug = SlugGenerator.slugify("a ".repeat(60));

        assertThat(slug.length()).isLessThanOrEqualTo(60);
        assertThat(slug).doesNotEndWith("-");
    }
}
