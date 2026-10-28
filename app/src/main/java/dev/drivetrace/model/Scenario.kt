package dev.drivetrace.model



enum class Scenario(val title: String, val subtitle: String) {
    CITY("Город", "Светофоры и плавные разгоны"), HIGHWAY("Трасса", "Крейсерский режим до 120 км/ч"),
    SPORT("Динамичный", "Ускорения и резкое торможение"), FAULT("Неисправность", "Перегрев, падение напряжения и обрыв")
}
