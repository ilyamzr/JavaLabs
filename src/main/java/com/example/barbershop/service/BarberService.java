package com.example.barbershop.service;

import com.example.barbershop.cache.Cache;
import com.example.barbershop.dto.AvailabilityDto;
import com.example.barbershop.dto.BarberDto;
import com.example.barbershop.mapper.BarberMapper;
import com.example.barbershop.model.Barber;
import com.example.barbershop.model.Location;
import com.example.barbershop.model.Offering;
import com.example.barbershop.model.Order;
import com.example.barbershop.repository.BarberRepository;
import com.example.barbershop.repository.LocationRepository;
import com.example.barbershop.repository.OfferingRepository;
import com.example.barbershop.repository.OrderRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BarberService {
    private static final String BARBER_NOT_FOUND = "Barber not found";
    private static final String OFFERING_NOT_FOUND = "Offering not found";
    private static final String LOCATION_NOT_FOUND = "Location not found";
    private static final String ALL_BARBERS_CACHE_KEY = "all_barbers";
    private static final String BARBER_CACHE_KEY_PREFIX = "barber_";
    private static final int SLOT_DURATION_MINUTES = 30;
    private static final int AVAILABILITY_DAYS = 7;
    private static final Logger logger = LoggerFactory.getLogger(BarberService.class);

    private final BarberRepository barberRepository;
    private final OfferingRepository offeringRepository;
    private final LocationRepository locationRepository;
    private final OrderRepository orderRepository;
    private final Cache cache;

    public List<BarberDto> findAll() {
        Optional<Object> cachedBarbers = cache.get(ALL_BARBERS_CACHE_KEY);
        if (cachedBarbers.isPresent()) {
            return (List<BarberDto>) cachedBarbers.get();
        }

        List<BarberDto> barbers = barberRepository.findAll().stream()
                .map(BarberMapper::toDto)
                .collect(Collectors.toList());

        cache.put(ALL_BARBERS_CACHE_KEY, barbers);

        return barbers;
    }

    public Optional<BarberDto> findById(Long id) {
        String cacheKey = BARBER_CACHE_KEY_PREFIX + id;

        Optional<Object> cachedBarber = cache.get(cacheKey);
        if (cachedBarber.isPresent()) {
            return Optional.of((BarberDto) cachedBarber.get());
        }

        Optional<BarberDto> barberDto = barberRepository.findById(id)
                .map(BarberMapper::toDto);

        barberDto.ifPresent(dto -> cache.put(cacheKey, dto));

        return barberDto;
    }

    public BarberDto save(BarberDto barberDto) {
        Barber barber = BarberMapper.toEntity(barberDto);
        Barber saved = barberRepository.save(barber);
        BarberDto savedDto = BarberMapper.toDto(saved);

        String cacheKey = BARBER_CACHE_KEY_PREFIX + saved.getBarberId();
        cache.remove(ALL_BARBERS_CACHE_KEY);
        cache.put(cacheKey, savedDto);

        return savedDto;
    }

    @Transactional
    public BarberDto updateBarber(Long id, BarberDto barberDto) {
        Barber barber = barberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException(BARBER_NOT_FOUND));
        barber.setName(barberDto.getName());
        barber.setAvailableDays(barberDto.getAvailableDays());
        barber.setStartTime(barberDto.getStartTime());
        barber.setEndTime(barberDto.getEndTime());
        Barber updated = barberRepository.save(barber);
        BarberDto updatedDto = BarberMapper.toDto(updated);

        String cacheKey = BARBER_CACHE_KEY_PREFIX + id;
        cache.put(cacheKey, updatedDto);

        cache.remove(ALL_BARBERS_CACHE_KEY);

        return updatedDto;
    }

    public void deleteById(Long id) {
        barberRepository.deleteById(id);

        String cacheKey = BARBER_CACHE_KEY_PREFIX + id;
        cache.remove(cacheKey);

        cache.remove(ALL_BARBERS_CACHE_KEY);
    }

    @Transactional
    public BarberDto addOfferingToBarber(Long barberId, Long offeringId) {
        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new RuntimeException(BARBER_NOT_FOUND));
        Offering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new RuntimeException(OFFERING_NOT_FOUND));

        barber.getOfferings().add(offering);
        offering.getBarbers().add(barber);
        barberRepository.save(barber);
        offeringRepository.save(offering);

        cache.remove(ALL_BARBERS_CACHE_KEY);

        return BarberMapper.toDto(barber);
    }

    @Transactional
    public BarberDto removeOfferingFromBarber(Long barberId, Long offeringId) {
        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new RuntimeException(BARBER_NOT_FOUND));
        Offering offering = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new RuntimeException(OFFERING_NOT_FOUND));

        barber.getOfferings().remove(offering);
        offering.getBarbers().remove(barber);
        barberRepository.save(barber);
        offeringRepository.save(offering);

        cache.remove(ALL_BARBERS_CACHE_KEY);

        return BarberMapper.toDto(barber);
    }

    @Transactional
    public BarberDto assignLocationToBarber(Long barberId, Long locationId) {
        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new RuntimeException(BARBER_NOT_FOUND));
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException(LOCATION_NOT_FOUND));

        barber.setLocation(location);
        location.getBarbers().add(barber);
        barberRepository.save(barber);
        locationRepository.save(location);

        cache.remove(ALL_BARBERS_CACHE_KEY);

        return BarberMapper.toDto(barber);
    }

    @Transactional
    public BarberDto removeLocationFromBarber(Long barberId, Long locationId) {
        Barber barber = barberRepository.findById(barberId)
                .orElseThrow(() -> new RuntimeException(BARBER_NOT_FOUND));
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new RuntimeException(LOCATION_NOT_FOUND));

        barber.setLocation(null);
        location.getBarbers().remove(barber);
        barberRepository.save(barber);
        locationRepository.save(location);

        cache.remove(ALL_BARBERS_CACHE_KEY);

        return BarberMapper.toDto(barber);
    }

    public List<BarberDto> getBarbersByLocationName(String locationName) {
        return barberRepository.findBarbersByLocationName(locationName).stream()
                .map(BarberMapper::toDto)
                .toList();
    }

    public List<BarberDto> getBarbersByLocationNameNative(String locationName) {
        return barberRepository.findBarbersByLocationNameNative(locationName).stream()
                .map(BarberMapper::toDto)
                .toList();
    }

    @Transactional
    public List<BarberDto> saveAll(List<BarberDto> barberDtos) {
        List<BarberDto> savedDtos = barberDtos.stream()
                .map(BarberMapper::toEntity)
                .map(barberRepository::save)
                .map(BarberMapper::toDto)
                .collect(Collectors.toList());

        cache.remove(ALL_BARBERS_CACHE_KEY);

        savedDtos.forEach(dto ->
                cache.put(BARBER_CACHE_KEY_PREFIX + dto.getBarberId(), dto)
        );

        return savedDtos;
    }
}
