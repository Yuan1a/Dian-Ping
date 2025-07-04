package com.zzy.medicalagent.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zzy.medicalagent.entity.Appointment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {
}
