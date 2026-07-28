package com.msa.hr.vo;

public class HrEmployeeCheckVo {
    private String hrResultCode;
    private String hrResultMessage;
    private String employeeNo;
    private String employeeName;
    private String employeeStatus;
    private boolean active;

    public String getHrResultCode() {
        return hrResultCode;
    }

    public void setHrResultCode(String hrResultCode) {
        this.hrResultCode = hrResultCode;
    }

    public String getHrResultMessage() {
        return hrResultMessage;
    }

    public void setHrResultMessage(String hrResultMessage) {
        this.hrResultMessage = hrResultMessage;
    }

    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeStatus() {
        return employeeStatus;
    }

    public void setEmployeeStatus(String employeeStatus) {
        this.employeeStatus = employeeStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
