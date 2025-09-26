package com.mwdle.bitwarden;

import com.mwdle.BitwardenGlobalConfig;
import com.mwdle.model.BitwardenStatus;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the BitwardenSessionManager class.
 */
@DisplayName("BitwardenSessionManager")
public class BitwardenSessionManagerTest {

    private MockedStatic<Jenkins> mockedJenkins;
    private MockedStatic<BitwardenGlobalConfig> mockedConfig;
    private MockedStatic<BitwardenCLI> mockedCli;

    private BitwardenSessionManager manager;

    @BeforeEach
    void setUp() {
        mockedJenkins = mockStatic(Jenkins.class);
        mockedConfig = mockStatic(BitwardenGlobalConfig.class);
        mockedCli = mockStatic(BitwardenCLI.class);

        when(Jenkins.get()).thenReturn(mock(Jenkins.class));
        when(BitwardenGlobalConfig.get()).thenReturn(mock(BitwardenGlobalConfig.class));

        manager = new BitwardenSessionManager();
    }

    @AfterEach
    void tearDown() {
        mockedJenkins.close();
        mockedConfig.close();
        mockedCli.close();
    }

    @Nested
    @DisplayName("getSessionToken() method")
    class GetSessionToken {

        @Test
        @DisplayName("should return cached token if it is valid")
        void shouldReturnCachedTokenWhenValid() throws Exception {
            Secret token = Secret.fromString("valid-cached-token");
            Field sessionTokenField = BitwardenSessionManager.class.getDeclaredField("sessionToken");
            sessionTokenField.setAccessible(true);
            sessionTokenField.set(manager, token);

            BitwardenStatus unlockedStatus = mock(BitwardenStatus.class);
            when(unlockedStatus.getStatus()).thenReturn("unlocked");
            mockedCli.when(() -> BitwardenCLI.status(token)).thenReturn(unlockedStatus);

            Secret resultToken = manager.getSessionToken();

            assertEquals(token, resultToken, "Should have returned the cached token.");

            // Verify that no login/unlock operations were attempted.
            mockedCli.verify(() -> BitwardenCLI.login(any()), never());
            mockedCli.verify(() -> BitwardenCLI.unlock(any()), never());
        }
    }
}
