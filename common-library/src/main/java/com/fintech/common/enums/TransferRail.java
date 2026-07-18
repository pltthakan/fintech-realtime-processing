package com.fintech.common.enums;

/**
 * Para transferinin hangi mutabakat kanalı üzerinden ilerlediğini belirtir.
 */
public enum TransferRail {
    INTERNAL, // Kullanıcının kendi hesapları arasında
    HAVALE,   // Aynı platformdaki başka bir hesaba
    EFT,      // Harici banka, standart mutabakat
    FAST      // Harici banka, anlık mutabakat
}
