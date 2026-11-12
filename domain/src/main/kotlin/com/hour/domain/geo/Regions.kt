package com.hour.domain.geo

data class Region(val province: String?, val district: String?)

/** 한국 주소 문자열에서 시·도와 시·군·구를 뽑는다. 시·도 이름은 짧은 표준형(서울, 경기, 강원 …)으로 맞춘다. */
object RegionParser {
    private val provinceAliases: List<Pair<String, String>> = listOf(
        "서울특별시" to "서울", "서울시" to "서울", "서울" to "서울",
        "부산광역시" to "부산", "부산" to "부산",
        "대구광역시" to "대구", "대구" to "대구",
        "인천광역시" to "인천", "인천" to "인천",
        "광주광역시" to "광주", "광주" to "광주",
        "대전광역시" to "대전", "대전" to "대전",
        "울산광역시" to "울산", "울산" to "울산",
        "세종특별자치시" to "세종", "세종" to "세종",
        "경기도" to "경기", "경기" to "경기",
        "강원특별자치도" to "강원", "강원도" to "강원", "강원" to "강원",
        "충청북도" to "충북", "충북" to "충북",
        "충청남도" to "충남", "충남" to "충남",
        "전북특별자치도" to "전북", "전라북도" to "전북", "전북" to "전북",
        "전라남도" to "전남", "전남" to "전남",
        "경상북도" to "경북", "경북" to "경북",
        "경상남도" to "경남", "경남" to "경남",
        "제주특별자치도" to "제주", "제주도" to "제주", "제주" to "제주",
    )

    val PROVINCES = listOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주")

    fun parse(address: String?): Region {
        if (address.isNullOrBlank()) return Region(null, null)
        val tokens = address.trim().split(Regex("\\s+"))
        val first = tokens.first()
        val province = provinceAliases.firstOrNull { (alias, _) -> first == alias }?.second
        val rest = if (province != null) tokens.drop(1) else tokens
        // 광주시(경기)처럼 시·도 이름과 같은 기초자치단체가 있으므로 '광주'는 다음 토큰이 시·군·구일 때만 시·도로 본다.
        val district = rest.firstOrNull { it.endsWith("시") || it.endsWith("군") || it.endsWith("구") }
        return Region(province, district)
    }
}

/**
 * 좌표로 시·도를 추정한다(오프라인 근사). 광역·특별시는 중심 반경 안이면 그 시로, 나머지는 가장 가까운 도 중심점으로 본다.
 * 경계 부근은 틀릴 수 있다. 주소에 시·도가 있으면 주소를 우선한다.
 */
object ProvinceLocator {
    private data class Center(val name: String, val lat: Double, val lon: Double, val radiusKm: Double = 0.0)

    private val metros = listOf(
        Center("서울", 37.5665, 126.9780, 17.0),
        Center("부산", 35.1796, 129.0756, 20.0),
        Center("대구", 35.8714, 128.6014, 17.0),
        Center("인천", 37.4563, 126.7052, 15.0),
        Center("광주", 35.1595, 126.8526, 13.0),
        Center("대전", 36.3504, 127.3845, 13.0),
        Center("울산", 35.5384, 129.3114, 20.0),
        Center("세종", 36.4800, 127.2890, 12.0),
    )
    private val provinces = listOf(
        Center("경기", 37.4138, 127.5183),
        Center("강원", 37.8228, 128.1555),
        Center("충북", 36.8000, 127.7000),
        Center("충남", 36.5184, 126.8000),
        Center("전북", 35.7175, 127.1530),
        Center("전남", 34.8679, 126.9910),
        Center("경북", 36.4919, 128.8889),
        Center("경남", 35.4606, 128.2132),
        Center("제주", 33.4996, 126.5312),
    )

    fun locate(lat: Double, lon: Double): String? {
        if (lat < 33.0 || lat > 38.7 || lon < 124.5 || lon > 131.0) return null // 한국 밖
        metros.firstOrNull { haversineMeters(lat, lon, it.lat, it.lon) / 1000.0 <= it.radiusKm }?.let { return it.name }
        return provinces.minByOrNull { haversineMeters(lat, lon, it.lat, it.lon) }?.name
    }
}
