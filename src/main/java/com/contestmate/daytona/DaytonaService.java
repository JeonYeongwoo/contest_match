package com.contestmate.daytona;

import io.daytona.sdk.Daytona;
import io.daytona.sdk.DaytonaConfig;
import io.daytona.sdk.Sandbox;
import io.daytona.sdk.model.CreateSandboxFromSnapshotParams;
import io.daytona.sdk.model.ExecuteResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DaytonaService {
    private final String apiKey;

    public DaytonaService(@Value("${contest-mate.daytona.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public DaytonaExecutionResponse executePython(String code) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "DAYTONA_API_KEY is not configured");
        }

        DaytonaConfig config = new DaytonaConfig.Builder().apiKey(apiKey).build();
        try (Daytona daytona = new Daytona(config)) {
            CreateSandboxFromSnapshotParams params = new CreateSandboxFromSnapshotParams();
            params.setLanguage("python");
            Sandbox sandbox = daytona.create(params);
            try {
                ExecuteResponse response = sandbox.getProcess().codeRun(code);
                return new DaytonaExecutionResponse(
                        sandbox.getId(), response.getResult(), response.getExitCode());
            } finally {
                sandbox.delete();
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Daytona sandbox execution failed", exception);
        }
    }
}
