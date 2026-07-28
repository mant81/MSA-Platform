package com.msa.hr.mapper;

import com.msa.hr.vo.HrEmployeeVo;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrEmployeeMapper {
    List<HrEmployeeVo> selectAll();
    HrEmployeeVo selectByEmployeeNo(String employeeNo);
}
