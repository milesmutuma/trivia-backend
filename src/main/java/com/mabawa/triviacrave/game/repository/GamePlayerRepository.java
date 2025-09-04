package com.mabawa.triviacrave.game.repository;

import com.mabawa.triviacrave.game.entity.GamePlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GamePlayerRepository extends JpaRepository<GamePlayer, Long> {
    
    List<GamePlayer> findByGameIdAndLeftAtIsNull(Long gameId);
    
    Optional<GamePlayer> findByGameIdAndUserIdAndLeftAtIsNull(Long gameId, Long userId);
    
    List<GamePlayer> findByUserIdAndLeftAtIsNull(Long userId);
    
    boolean existsByGameIdAndUserIdAndLeftAtIsNull(Long gameId, Long userId);
    
    long countByGameIdAndLeftAtIsNull(Long gameId);
    
    Optional<GamePlayer> findByGameIdAndIsHostTrueAndLeftAtIsNull(Long gameId);
    
    List<GamePlayer> findByGameIdAndIsReadyTrueAndLeftAtIsNull(Long gameId);
}