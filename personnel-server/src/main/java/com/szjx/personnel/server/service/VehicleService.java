package com.szjx.personnel.server.service;

import com.szjx.personnel.server.dao.PersonnelDao;
import com.szjx.personnel.server.dao.VehicleDao;
import com.szjx.personnel.server.model.Vehicle;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 车辆业务
 */
@Service
public class VehicleService {

    private final VehicleDao vehicleDao;
    private final PersonnelDao personnelDao;

    public VehicleService(VehicleDao vehicleDao, PersonnelDao personnelDao) {
        this.vehicleDao = vehicleDao;
        this.personnelDao = personnelDao;
    }

    public List<Vehicle> query(String keyword, String status, int page, int size) {
        return vehicleDao.query(keyword, status, page, size);
    }

    public long count(String keyword, String status) {
        return vehicleDao.count(keyword, status);
    }

    public Vehicle create(Vehicle v) {
        validate(v);
        long id = vehicleDao.insert(v);
        return vehicleDao.findById(id);
    }

    public Vehicle update(Long id, Vehicle v) {
        if (vehicleDao.findById(id) == null) {
            throw new IllegalArgumentException("车辆不存在");
        }
        validate(v);
        v.setId(id);
        vehicleDao.update(v);
        return vehicleDao.findById(id);
    }

    public void delete(Long id) {
        vehicleDao.delete(id);
    }

    private void validate(Vehicle v) {
        if (!StringUtils.hasText(v.getPlateNo())) {
            throw new IllegalArgumentException("车牌号不能为空");
        }
        if (v.getDriverId() != null && v.getDriverId() > 0
                && personnelDao.findById(v.getDriverId()) == null) {
            throw new IllegalArgumentException("所选驾驶员不存在");
        }
    }
}
