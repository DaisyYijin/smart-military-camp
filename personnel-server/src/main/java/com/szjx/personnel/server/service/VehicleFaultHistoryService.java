package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.VehicleFaultHistoryDao;
import com.szjx.personnel.server.model.VehicleFaultHistory;
import com.szjx.personnel.server.util.Dates;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class VehicleFaultHistoryService {
    private final VehicleFaultHistoryDao dao;

    public VehicleFaultHistoryService(VehicleFaultHistoryDao dao) {
        this.dao = dao;
    }

    public List<VehicleFaultHistory> list(Long vehicleId) {
        return dao.findByVehicleId(vehicleId);
    }

    public VehicleFaultHistory create(Long vehicleId, VehicleFaultHistory record) {
        validate(record);
        record.setVehicleId(vehicleId);
        record.setId(dao.insert(record));
        return record;
    }

    public VehicleFaultHistory update(Long vehicleId, Long id, VehicleFaultHistory record) {
        validate(record);
        dao.update(vehicleId, id, record);
        record.setId(id);
        record.setVehicleId(vehicleId);
        return record;
    }

    public void delete(Long vehicleId, Long id) {
        dao.delete(vehicleId, id);
    }

    private void validate(VehicleFaultHistory record) {
        if (record == null) throw new IllegalArgumentException("故障记录不能为空");
        String date = record.getFaultDate() == null ? "" : record.getFaultDate().trim();
        if (!Dates.validOrEmpty(date) || date.isEmpty()) throw new IllegalArgumentException("故障时间不能为空且格式应为 yyyy-MM-dd");
        if (!StringUtils.hasText(record.getDescription())) throw new IllegalArgumentException("故障情况不能为空");
        String description = record.getDescription().trim();
        if (description.length() > 4000) throw new IllegalArgumentException("故障情况不能超过4000个字符");
        record.setFaultDate(date);
        record.setDescription(description);
    }
}
