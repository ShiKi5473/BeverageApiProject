package tw.niels.beverage_api_project.modules.brand.dto;

import jakarta.validation.constraints.NotEmpty;

public class CreateBrandRequestDto {

    @NotEmpty
    private String name;

    private String contactPerson;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }
}