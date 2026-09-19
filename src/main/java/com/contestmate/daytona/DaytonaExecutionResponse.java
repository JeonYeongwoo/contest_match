package com.contestmate.daytona;

public class DaytonaExecutionResponse {
    private final String sandboxId;
    private final String result;
    private final int exitCode;

    public DaytonaExecutionResponse(String sandboxId, String result, int exitCode) {
        this.sandboxId = sandboxId;
        this.result = result;
        this.exitCode = exitCode;
    }

    public String getSandboxId() { return sandboxId; }
    public String getResult() { return result; }
    public int getExitCode() { return exitCode; }
}
