package com.hrms.app.service.impl;

import com.hrms.app.Enum.AttendanceStatus;
import com.hrms.app.dto.requestDto.AttendanceRequestDto;
import com.hrms.app.dto.responseDto.AddAttendanceResponseDto;
import com.hrms.app.dto.responseDto.GetAttendanceResponseDto;
import com.hrms.app.entity.Attendance;
import com.hrms.app.entity.Employee;
import com.hrms.app.mapper.AttendanceMapper;
import com.hrms.app.repository.AttendanceRepository;
import com.hrms.app.repository.EmpInfoRepository;
import com.hrms.app.service.AttendanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;


@Service
public class AttendanceServiceImpl implements AttendanceService {

    @Autowired
    AttendanceRepository attendanceRepository;

    @Autowired
    EmpInfoRepository empInfoRepository;

    @Override
    public AddAttendanceResponseDto markAttendance(AttendanceRequestDto attendanceRequestDto) {

        Employee employee = empInfoRepository.findByEmpEmail(attendanceRequestDto.getEmpEmail());

        if(employee == null) {
            throw new RuntimeException("Invalid Employee Email Id");
        }

        if(employee.isAttendanceMarked()) {
            throw new RuntimeException("Your attendance is already recorded for today");
        }

        Attendance attendance = AttendanceMapper.AttendanceRequestDtoToAttendance(attendanceRequestDto);

        attendance.setEmployee(employee);

        employee.getAttendanceList().add(attendance);

        employee.setAttendanceMarked(true);

        empInfoRepository.save(employee);

        return AttendanceMapper.AttendanceToAddAttendanceResponseDto(attendance);
    }

    @Override
    public List<GetAttendanceResponseDto> getAttendanceList(String empEmail) {

        Employee employee = empInfoRepository.findByEmpEmail(empEmail);

        if(employee == null) {
            throw new RuntimeException("Invalid Employee Email Id");
        }

        List<Attendance> attendanceList = employee.getAttendanceList();

        return attendanceList.stream().map(AttendanceMapper::AttendanceToGetAttendanceResponseDto).toList();

    }

    @Override
    public GetAttendanceResponseDto punchOut(String empEmail) {
        Optional<Attendance> optionalAttendance = attendanceRepository.findByEmployeeEmailAndDate(empEmail, LocalDate.now());

        if(optionalAttendance.isEmpty()) {
            throw new RuntimeException("You didn't punch in today");
        }

        Attendance attendance = optionalAttendance.get();

        if(!attendance.getEmployee().isAttendanceMarked())
            throw new RuntimeException("Your have to punch in first!!");

        if(attendance.getPunchOutTime() != null)
            throw new RuntimeException("Your punch out time for today is already recorded");

        if(attendance.getAttendanceStatus().equals(AttendanceStatus.MARKED_FOR_LEAVE))
            throw new RuntimeException("Invalid request, you are marked for leave today");

        attendance.setPunchOutTime(LocalTime.now());

        attendance.setActiveTime(ChronoUnit.MINUTES.between(attendance.getPunchInTime(), attendance.getPunchOutTime()) / 60.0);

        attendanceRepository.save(attendance);

        return AttendanceMapper.AttendanceToGetAttendanceResponseDto(attendance);
    }

    @Override
    public List<GetAttendanceResponseDto> getAttendanceListLastMonth(String empEmail) {

        Employee employee = empInfoRepository.findByEmpEmail(empEmail);

        if(employee == null) {
            throw new RuntimeException("Invalid Employee Email Id");
        }

        LocalDate date = LocalDate.now().minusMonths(1);
        List<Attendance> attendanceList = attendanceRepository.findAttentionAfterDate(empEmail, date);

        return attendanceList.stream().map(AttendanceMapper::AttendanceToGetAttendanceResponseDto).toList();

    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Calcutta")
    public void resetAttendanceMarked() {

        List<Employee> employeeList = empInfoRepository.findAll();

        if(employeeList.isEmpty()) {
            return;
        }

        for (Employee employee : employeeList) {
            employee.setAttendanceMarked(false);
            empInfoRepository.save(employee);
        }

    }

    @Scheduled(cron = "0 0 12 * * MON-SAT", zone = "Asia/Calcutta")
    public void recordEmployeesOnLeave() {

        List<Employee> employeeList = empInfoRepository.getAbsentEmployeeList();

        for (Employee employee : employeeList) {

            Attendance attendance = new Attendance(AttendanceStatus.NOT_MARKED, employee);
            employee.getAttendanceList().add(attendance);
            employee.setAttendanceMarked(true);
            empInfoRepository.save(employee);

        }
    }

}
