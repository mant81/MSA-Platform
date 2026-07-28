package com.msa.hr.controller;

import com.msa.hr.service.HrEmployeeService;
import com.msa.hr.vo.HrEmployeeCheckVo;
import com.msa.hr.vo.HrEmployeeVo;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hr")
public class HrEmployeeController {
    private final HrEmployeeService hrEmployeeService;

    public HrEmployeeController(HrEmployeeService hrEmployeeService) {
        this.hrEmployeeService = hrEmployeeService;
    }

    @GetMapping("/employees")
    public List<HrEmployeeVo> selectAll() {
        return hrEmployeeService.selectAll();
    }

    @GetMapping("/employees/{employeeNo}")
    public HrEmployeeCheckVo selectByEmployeeNo(@PathVariable String employeeNo) {
        HrEmployeeVo vo = hrEmployeeService.selectByEmployeeNo(employeeNo);
        boolean exists = vo != null && "ACTIVE".equalsIgnoreCase(vo.getStatus());
        HrEmployeeCheckVo response = new HrEmployeeCheckVo();
        response.setHrResultCode(exists ? "HR0000" : "HR0404");
        response.setHrResultMessage(exists ? "ACTIVE EMPLOYEE" : "EMPLOYEE NOT FOUND OR INACTIVE");
        response.setEmployeeNo(employeeNo);
        response.setEmployeeName(vo == null ? null : vo.getEmployeeName());
        response.setEmployeeStatus(vo == null ? "NOT_FOUND" : vo.getStatus());
        response.setActive(exists);
        return response;
    }
}
