package com.krishna.userservice.controller;

import com.krishna.userservice.entity.Address;
import com.krishna.userservice.service.AddressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/{userId}/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    public ResponseEntity<Address> createAddress(
            @PathVariable Long userId,
            @RequestBody Address address) {

        address.setUserId(userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(addressService.createAddress(address));
    }

    @GetMapping
    public ResponseEntity<List<Address>> getAddresses(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                addressService.getAddressesByUserId(userId)
        );
    }

    @GetMapping("/{addressId}")
    public ResponseEntity<Address> getAddressById(
            @PathVariable Long addressId) {

        return ResponseEntity.ok(
                addressService.getAddressById(addressId)
        );
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<Address> updateAddress(
            @PathVariable Long addressId,
            @RequestBody Address address) {

        return ResponseEntity.ok(
                addressService.updateAddress(addressId, address)
        );
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @PathVariable Long addressId) {

        addressService.deleteAddress(addressId);

        return ResponseEntity.noContent().build();
    }
}