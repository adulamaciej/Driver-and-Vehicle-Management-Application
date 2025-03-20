package org.example.driverandfleetmanagementapp.service;


import lombok.RequiredArgsConstructor;
import org.example.driverandfleetmanagementapp.dto.VehicleDto;
import org.example.driverandfleetmanagementapp.exception.*;
import org.example.driverandfleetmanagementapp.mapper.VehicleMapper;
import org.example.driverandfleetmanagementapp.model.Driver;
import org.example.driverandfleetmanagementapp.model.Vehicle;
import org.example.driverandfleetmanagementapp.repository.DriverRepository;
import org.example.driverandfleetmanagementapp.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;



@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleServiceImpl implements VehicleService {
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final VehicleMapper vehicleMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<VehicleDto> getAllVehicles(int page, int size) {
        log.info("Getting all vehicles");
        log.debug("getting vehicles with pagination - page: {}, size: {}, method=getAllVehicles", page, size);
        Pageable pageable = PageRequest.of(page, size);
        Page<Vehicle> vehiclesPage = vehicleRepository.findAll(pageable);

        return vehiclesPage.map(vehicleMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "vehicles", key = "'vehicle:' + #id")
    public VehicleDto getVehicleById(Integer id) {
        log.info("Getting vehicles by ID");
        log.debug("Getting vehicle by ID: {}, method=getVehicleById", id);
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with ID " + id + " not found"));
        return vehicleMapper.toDto(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "vehicles", key = "'licensePlate:' + #licensePlate")
    public VehicleDto getVehicleByLicensePlate(String licensePlate) {
        log.info("Getting vehicles by license plate");
        log.debug("Getting vehicle by license plate: {}, method=getVehicleByLicensePlate", licensePlate);
        Vehicle vehicle = vehicleRepository.findByLicensePlate(licensePlate)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with license plate " + licensePlate + " not found"));
        return vehicleMapper.toDto(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VehicleDto> getVehiclesByStatus(Vehicle.VehicleStatus status) {
        log.info("Getting vehicles by status");
        log.debug("Getting vehicles by status: {}, method=getVehiclesByStatus", status);
        return vehicleMapper.toDtoList(vehicleRepository.findByStatus(status));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "vehicles", key = "'brandAndModel:' + #brand + ':' + #model")
    public List<VehicleDto> getVehiclesByBrandAndModel(String brand, String model) {
        log.info("Getting vehicles by brand and model");
        log.debug("Getting vehicles by brand: {} and model: {}, method=getVehiclesByBrandAndModel", brand, model);
        return vehicleMapper.toDtoList(vehicleRepository.findByBrandAndModel(brand, model));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "vehicles", key = "'type:' + #type")
    public List<VehicleDto> getVehiclesByType(Vehicle.VehicleType type) {
        log.info("Getting vehicles by type");
        log.debug("Getting vehicles by type: {}, method=getVehiclesByType", type);
        return vehicleMapper.toDtoList(vehicleRepository.findByType(type));
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "vehicles", key = "'driver:' + #driverId")
    public List<VehicleDto> getVehiclesByDriverId(Integer driverId) {
        log.info("Getting vehicles by driver");
        log.debug("Getting vehicles by driver ID: {}, method=getVehiclesByDriverId", driverId);
        driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver with ID " + driverId + " not found"));
        return vehicleMapper.toDtoList(vehicleRepository.findByDriverId(driverId));
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "'licensePlate:' + #vehicleDto.licensePlate"),
            @CacheEvict(value = "vehicles", key = "'type:' + #vehicleDto.type")
    })
    public VehicleDto createVehicle(VehicleDto vehicleDto) {
        log.info("Creating vehicle");
        log.debug("Creating new vehicle: {}, method=createVehicle", vehicleDto);
        if (vehicleRepository.findByLicensePlate(vehicleDto.getLicensePlate()).isPresent()) {
            throw new ResourceConflictException("Vehicle with license plate " + vehicleDto.getLicensePlate() + " already exists");
        }
        Vehicle vehicle = vehicleMapper.toEntity(vehicleDto);

        validateTechnicalInspectionDate(vehicle);

        if (vehicleDto.getDriver() != null && vehicleDto.getDriver().getId() != null) {
            vehicle.setDriver(driverRepository.findById(vehicleDto.getDriver().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver with ID " + vehicleDto.getDriver().getId() + " not found")));
        }
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toDto(vehicle);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "'vehicle:' + #id"),
            @CacheEvict(value = "vehicles", key = "'licensePlate:' + #vehicleDto.licensePlate"),
            @CacheEvict(value = "vehicles", key = "'type:' + #vehicleDto.type"),
            @CacheEvict(value = "vehicles", key = "'brandAndModel:' + #vehicleDto.brand + ':' + #vehicleDto.model"),
            @CacheEvict(value = "vehicles", key = "'driver:' + #vehicleDto.driver?.id")
    })
    public VehicleDto updateVehicle(Integer id, VehicleDto vehicleDto) {
        log.info("Updating vehicle");
        log.debug("Updating vehicle with ID: {}, method=updateVehicle", id);
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with ID " + id + " not found"));
        vehicleRepository.findByLicensePlate(vehicleDto.getLicensePlate())
                .filter(v -> !v.getId().equals(id))
                .ifPresent(v -> {
                    throw new ResourceConflictException("License plate " + vehicleDto.getLicensePlate() + " already in use");
                });

        vehicleMapper.updateVehicleFromDto(vehicleDto, vehicle);

        validateTechnicalInspectionDate(vehicle);

        if (vehicleDto.getDriver() != null && vehicleDto.getDriver().getId() != null) {
            vehicle.setDriver(driverRepository.findById(vehicleDto.getDriver().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Driver with ID " + vehicleDto.getDriver().getId() + " not found")));
        } else {
            vehicle.setDriver(null);
        }
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toDto(vehicle);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "'vehicle:' + #id"),
    })
    public void deleteVehicle(Integer id) {
        log.info("Deleting vehicle with ID");
        log.debug("Deleting vehicle with ID: {}, method=deleteVehicle", id);
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with ID " + id + " not found"));

        if (vehicle.getDriver() != null && vehicle.getStatus() == Vehicle.VehicleStatus.IN_USE) {
            throw new BusinessLogicException("Cannot delete vehicle with ID " + id + " as it is currently in use by driver " + vehicle.getDriver().getId());
        }

        if (vehicle.getDriver() != null) {
            Driver driver = vehicle.getDriver();
            driver.getVehicles().remove(vehicle);
            vehicle.setDriver(null);
            driverRepository.save(driver);
        }

        vehicleRepository.delete(vehicle);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "'vehicle:' + #vehicleId"),
            @CacheEvict(value = "vehicles", key = "'driver:' + #driverId"),
    })
    public VehicleDto assignVehicleToDriver(Integer vehicleId, Integer driverId) {
        log.info("Assigning vehicle ID to Driver ID");
        log.debug("Assigning vehicle ID: {} to driver ID: {}, method=assignVehicleToDriver", vehicleId, driverId);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with ID " + vehicleId + " not found"));
        if (vehicle.getStatus() == Vehicle.VehicleStatus.OUT_OF_ORDER) {
            throw new BusinessLogicException("Cannot assign vehicle with status OUT_OF_ORDER to a driver");
        }

        if (vehicle.getDriver() != null) {
            throw new ResourceConflictException("Vehicle is already assigned to a driver");
        }

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Driver with ID " + driverId + " not found"));

        if (driver.getStatus() == Driver.DriverStatus.SUSPENDED) {
            throw new BusinessLogicException("Cannot assign vehicle to suspended driver");
        }

        if (!canDriverOperateVehicle(driver.getLicenseType(), vehicle.getType())) {
            throw new BusinessLogicException("Driver's license type " + driver.getLicenseType() +
                    " does not allow operating vehicle of type " + vehicle.getType());
        }

        vehicle.setDriver(driver);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toDto(vehicle);
    }

    private boolean canDriverOperateVehicle(Driver.LicenseType licenseType, Vehicle.VehicleType vehicleType) {
        log.debug("Checking if license type {} can operate vehicle type {}", licenseType, vehicleType);

        if (licenseType == null) {
            throw new BusinessLogicException("Driver has an unknown or invalid license type");
        }
        return switch (licenseType) {
            case B -> vehicleType == Vehicle.VehicleType.CAR;
            case C -> vehicleType == Vehicle.VehicleType.CAR ||
                    vehicleType == Vehicle.VehicleType.VAN ||
                    vehicleType == Vehicle.VehicleType.TRUCK;
            case D -> vehicleType == Vehicle.VehicleType.CAR ||
                    vehicleType == Vehicle.VehicleType.BUS;
            case CE -> vehicleType == Vehicle.VehicleType.CAR ||
                    vehicleType == Vehicle.VehicleType.VAN ||
                    vehicleType == Vehicle.VehicleType.TRUCK;
            case DE -> vehicleType == Vehicle.VehicleType.CAR ||
                    vehicleType == Vehicle.VehicleType.BUS;
            default -> false;
        };
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "'vehicle:' + #vehicleId"),
    })
    public VehicleDto removeDriverFromVehicle(Integer vehicleId) {
        log.info("Removing driver from vehicle ID");
        log.debug("Removing driver from vehicle ID: {}, method=removeDriverFromVehicle", vehicleId);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with ID " + vehicleId + " not found"));
        if (vehicle.getDriver() == null) {
            throw new BusinessLogicException("Vehicle has no assigned driver");
        }
        vehicle.setDriver(null);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toDto(vehicle);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "'vehicle:' + #id"),
    })
    public VehicleDto updateVehicleMileage(Integer id, Double mileage) {
        log.info("Updating mileage for vehicle ID");
        log.debug("Updating mileage for vehicle ID: {} to {}, method=updateVehicleMileage", id, mileage);
        if (mileage < 0) {
            throw new BusinessLogicException("Mileage cannot be negative");
        }
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with ID " + id + " not found"));
        vehicle.setMileage(mileage);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toDto(vehicle);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "vehicles", key = "'vehicle:' + #id"),
            @CacheEvict(value = "vehicles", key = "'status:' + #status")
    })
    public VehicleDto updateVehicleStatus(Integer id, Vehicle.VehicleStatus status) {
        log.info("Updating status for vehicles");
        log.debug("Updating status for vehicle ID: {} to {}, method=updateVehicleStatus", id, status);
        Vehicle vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle with ID " + id + " not found"));
        vehicle.setStatus(status);
        vehicle = vehicleRepository.save(vehicle);
        return vehicleMapper.toDto(vehicle);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateTechnicalInspectionDate(Vehicle vehicle) {
        LocalDate now = LocalDate.now();
        LocalDate inspectionDate = vehicle.getTechnicalInspectionDate();

        if (inspectionDate.isBefore(now.plusMonths(1))) {
            log.warn("Technical inspection for vehicle {} is expiring soon on {}",
                    vehicle.getLicensePlate(), inspectionDate);
        }

        if (inspectionDate.isBefore(now)) {
            log.error("Technical inspection has expired for vehicle {} on {}",
                    vehicle.getLicensePlate(), inspectionDate);
            throw new BusinessLogicException("Technical inspection has expired for vehicle with license plate "
                    + vehicle.getLicensePlate() + ". Inspection date: " + inspectionDate);
        }
    }
}