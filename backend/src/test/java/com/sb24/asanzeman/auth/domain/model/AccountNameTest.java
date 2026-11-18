package com.sb24.asanzeman.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountNameTest {

    @Test
    void removesTheSuffixIgnoringCase() {
        assertThat(AccountName.fromLogin("ali.rezaei@sb24.ir", "@sb24.ir").value()).isEqualTo("ali.rezaei");
        assertThat(AccountName.fromLogin(" Ali.Rezaei@SB24.IR ", "@sb24.ir").value()).isEqualTo("Ali.Rezaei");
    }

    @Test
    void theSuffixMatchDoesNotDependOnTheServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); // in Turkish, "I".toLowerCase() is a dotless i
            assertThat(AccountName.fromLogin("ALI@SB24.IR", "@sb24.ir").value()).isEqualTo("ALI");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void keepsAPlainNameAndAForeignSuffix() {
        assertThat(AccountName.fromLogin("ali.rezaei", "@sb24.ir").value()).isEqualTo("ali.rezaei");
        assertThat(AccountName.fromLogin("ali@other.ir", "@sb24.ir").value()).isEqualTo("ali@other.ir");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void withoutASuffixSettingNothingIsRemoved(String suffix) {
        assertThat(AccountName.fromLogin("ali@sb24.ir", suffix).value()).isEqualTo("ali@sb24.ir");
    }

    @Test
    void aLoginThatIsOnlyTheSuffixIsRefused() {
        assertThatThrownBy(() -> AccountName.fromLogin("@sb24.ir", "@sb24.ir"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingLoginIsRefused() {
        assertThatThrownBy(() -> AccountName.fromLogin(null, "@sb24.ir"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("accountName is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aBlankNameIsRefused(String value) {
        assertThatThrownBy(() -> new AccountName(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
