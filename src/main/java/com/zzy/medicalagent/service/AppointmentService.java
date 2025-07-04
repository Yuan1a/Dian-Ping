package com.zzy.medicalagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zzy.medicalagent.entity.Appointment;

public interface AppointmentService extends IService<Appointment> {
    Appointment getOne(Appointment appointment);
}
