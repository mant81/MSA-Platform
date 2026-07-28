package com.msa.hr.service;

import com.msa.hr.mapper.HrEmployeeMapper;
import com.msa.hr.vo.HrEmployeeVo;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HrEmployeeService {
    private final HrEmployeeMapper hrEmployeeMapper;

    public HrEmployeeService(HrEmployeeMapper hrEmployeeMapper) {
        this.hrEmployeeMapper = hrEmployeeMapper;
    }

    public List<HrEmployeeVo> selectAll() {
        return hrEmployeeMapper.selectAll();
    }

    public HrEmployeeVo selectByEmployeeNo(String employeeNo) {
        return hrEmployeeMapper.selectByEmployeeNo(employeeNo);
    }
}
