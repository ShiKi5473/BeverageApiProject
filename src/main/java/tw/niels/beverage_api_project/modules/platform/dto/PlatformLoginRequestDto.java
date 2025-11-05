package tw.niels.beverage_api_project.modules.platform.dto;

import jakarta.validation.constraints.NotEmpty;

public class PlatformLoginRequestDto {
    @NotEmpty
    private String username;
    @NotEmpty
    private String password;

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}