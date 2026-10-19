package com.finanzas.api.transaccion;

import com.finanzas.api.support.IntegrationTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Window/range params drive per-bucket queries; out-of-range values must be a 400,
// never a slow query or a 500.
class ParametrosAcotadosIntegrationTest extends IntegrationTestSupport {

    // url, extra param (name=value or empty), bounded param, value
    @ParameterizedTest
    @CsvSource({
            "/api/v1/finanzas/tendencia-mensual,           , meses,   0",
            "/api/v1/finanzas/tendencia-mensual,           , meses,  25",
            "/api/v1/finanzas/tendencia,  granularidad=MES , ventana, 0",
            "/api/v1/finanzas/tendencia,  granularidad=MES , ventana, 25",
            "/api/v1/finanzas/tendencia,  granularidad=SEMANA, ventana, 0",
            "/api/v1/finanzas/tendencia,  granularidad=SEMANA, ventana, 53",
            "/api/v1/finanzas/ingresos-por-dia-semana,     , ventana, 0",
            "/api/v1/finanzas/ingresos-por-dia-semana,     , ventana, 53",
            "/api/v1/finanzas/metas/historial,             , meses,   0",
            "/api/v1/finanzas/metas/historial,             , meses, 1000000",
            "/api/v1/finanzas/cuota-diaria,       meta=3000, dias,   -1",
            "/api/v1/finanzas/cuota-diaria,       meta=3000, dias,   32",
            "/api/v1/finanzas/progreso-metas,     meta=3000, dias,   -1",
            "/api/v1/finanzas/progreso-metas,     meta=3000, dias,   32",
    })
    void fueraDeRango_devuelve400(String url, String extra, String param, String valor) throws Exception {
        mockMvc.perform(request(url, extra, param, valor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRO_INVALIDO"));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/finanzas/tendencia-mensual,           , meses,   1",
            "/api/v1/finanzas/tendencia-mensual,           , meses,  24",
            "/api/v1/finanzas/tendencia,  granularidad=MES , ventana, 24",
            "/api/v1/finanzas/tendencia,  granularidad=SEMANA, ventana, 52",
            "/api/v1/finanzas/ingresos-por-dia-semana,     , ventana, 52",
            "/api/v1/finanzas/metas/historial,             , meses,  24",
            "/api/v1/finanzas/cuota-diaria,       meta=3000, dias,    0",
            "/api/v1/finanzas/cuota-diaria,       meta=3000, dias,   31",
            "/api/v1/finanzas/progreso-metas,     meta=3000, dias,    0",
            "/api/v1/finanzas/progreso-metas,     meta=3000, dias,   31",
    })
    void enLosBordes_devuelve200(String url, String extra, String param, String valor) throws Exception {
        mockMvc.perform(request(url, extra, param, valor))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder request(String url, String extra, String param, String valor) {
        MockHttpServletRequestBuilder req = get(url).header(AUTH, tokenDe(crearUsuario())).param(param, valor);
        if (extra != null) {
            String[] kv = extra.split("=");
            req.param(kv[0], kv[1]);
        }
        return req;
    }
}
