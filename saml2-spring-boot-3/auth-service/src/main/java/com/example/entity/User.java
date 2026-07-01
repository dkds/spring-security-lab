package com.example.entity;

import com.example.dto.UserAuthority;
import com.example.dto.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Data
@Entity
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String email;
    private String password;
    @OneToMany(fetch = FetchType.LAZY)
    private List<AuthProvider> authProviders;
    private UserRole role;
    private boolean active = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == UserRole.ADMIN) {
            return Stream.of(UserAuthority.ADMIN, UserAuthority.USERS_LIST, UserAuthority.PROVIDERS_LIST)
                    .map(UserAuthority::name)
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        } else if (role == UserRole.USER) {
            return Stream.of(UserAuthority.USERS_LIST, UserAuthority.PROVIDERS_LIST)
                    .map(UserAuthority::name)
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        }
        return List.of();
    }

    @Override
    public String getUsername() {
        return email;
    }
}
