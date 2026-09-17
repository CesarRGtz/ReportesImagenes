# Reportes y cotizaciones TEOSA

## Capas

- `domain`: documentos, partidas, importes decimales, validaciones, plantillas, versiones y reglas de distribución. No depende de JavaFX, Gson, HTTP ni PDF.
- `application`: operaciones de guardado, historial y sincronización. `AppServices` recibe puertos para documentos locales, conexión, archivos adjuntos y plantillas. No construye adaptadores ni depende de las pantallas.
- `application/port`: contratos `LocalDocuments`, `RemoteDocuments`, `ConnectionRuntime`, `AssetCodec`, `TemplateCache`, `DocumentCodec` y `DocumentOutput`.
- `infrastructure`: implementaciones JSON, archivos, HTTP, descubrimiento en red y PDF. Implementa los contratos de aplicación.
- `presentation`: controladores JavaFX, edición de plantillas, vista previa y diálogo de impresión. Los controladores reciben servicios, serialización y salida mediante constructor. El controlador fotográfico conserva sus controles y distribución existentes.
- `App` y `AppContext`: composición de dependencias y ciclo de vida. Los tres lanzadores comparten esta misma base.

`ArchitectureSmokeTest` impide dependencias desde dominio y aplicación hacia infraestructura, presentación o bibliotecas de interfaz/serialización/PDF. La compilación modular declara explícitamente los paquetes abiertos a FXML y Gson.

## Tres aplicaciones

| Ejecutable | Inicio | Papel |
| --- | --- | --- |
| Reportes TEOSA | Reportes fotográficos | Secundaria, guarda localmente y sincroniza |
| Cotizaciones TEOSA | Cotización | Secundaria, guarda localmente y sincroniza |
| Servidor TEOSA | Menú principal para elegir el sistema | Servidor de ambos módulos |

El servidor inicia en un menú principal con las opciones Reportes fotográficos y Cotizaciones. Cada editor incluye un botón Menú principal exclusivo del servidor. Volver al inicio conserva los dos paneles, incluyendo documentos en edición. Cotización comparte los estilos, tarjetas, panel lateral y barra de acciones de Reportes. Cada ejecutable fija su papel; ya no se cambia entre servidor y secundaria desde el formulario.

## Compatibilidad y almacenamiento

Los nombres de campos JSON de los reportes previos se conservan. Mover clases de paquete no modifica el formato de los archivos. Un documento sin `quotation` sigue siendo un reporte fotográfico. Las nuevas cotizaciones contienen `quotation` y no contienen un reporte fotográfico ficticio.

Reportes y servidor conservan `Documents/TEOSA Reportes`. La aplicación secundaria de cotizaciones utiliza `Documents/TEOSA Reportes/Cotizaciones`. El servidor guarda ambos tipos en su almacén versionado existente; los historiales de la interfaz se filtran por tipo. No se borran ni convierten los datos existentes al iniciar.

Las plantillas de cotización tienen una clave de almacenamiento independiente; una plantilla de reporte y una de cotización pueden compartir nombre. Las modificaciones de plantillas se guardan localmente antes de intentar sincronizarlas. Las versiones de documentos pendientes también se conservan al reiniciar. Se conserva el comportamiento previo de eliminación de documentos: sin conexión afecta a la copia local, no programa un borrado remoto.

Las rutas HTTP existentes se conservan. Las listas sin `kind=ALL` devuelven solamente reportes y plantillas fotográficas para clientes anteriores. Para sincronizar cotizaciones se necesita el servidor nuevo. Los importes se calculan con `BigDecimal`, redondeo de partidas a dos decimales e IVA sobre el subtotal. Cantidades enteras positivas y condiciones de pago del Excel: CONTADO, A 30 DIAS, A CONVENIR, A CREDITO.

El nuevo PDF utiliza los datos del Excel como referencia de estructura: encabezado, folio, cliente/contacto/fecha/ciudad, servicio/SP/pago, partidas y alcances, notas y totales. La vista previa de cotización se renderiza desde el mismo generador utilizado para exportar e imprimir. Conserva el logotipo de encabezado, pero no incluye editor de fotografías ni evidencias.

## Compilación y comprobaciones

En PowerShell, desde el proyecto:

```powershell
./generar-ejecutable.ps1 -SoloCompilar -BuildDirectory build-clean
./verificar.ps1 -TestFilter ArchitectureSmokeTest
./verificar.ps1 -TestFilter QuotationSmokeTest
./verificar.ps1 -TestFilter DesktopSmokeTest
./generar-ejecutable.ps1 -BuildDirectory build-clean -OutputDirectory paquete-tres-modulos
```

El script utiliza el JDK instalado y la caché Maven de la configuración original. `-Mode Reports`, `-Mode Quotations` o `-Mode Server` permite producir un solo paquete. Sin `-Mode`, genera los tres. Cada aplicación incluye su runtime; hay que distribuir su carpeta completa.

Las pruebas nuevas cubren límites entre capas, validaciones y redondeo, compatibilidad JSON, guardado sin conexión, subida automática a un servidor HTTP local, versiones, plantillas homónimas, PDF de una y varias páginas, personalización y arranque/cambio de paneles. Las pruebas de escritorio usan directorios temporales.

## Pruebas heredadas

La copia original con los cambios de firmas ya presenta fallos en `BorderAtPageEndSmokeTest` y `PhotoBorderModeSmokeTest`: esperan recuentos de páginas anteriores a la incorporación del bloque de firmas. Los mismos fallos se reprodujeron antes y después de la refactorización. No se modificaron esas expectativas ni el comportamiento fotográfico para ocultarlos. `PageBorderSmokeTest` es un auxiliar que recibe un PDF y es llamado por la prueba de borde; no se ejecuta solo.

La impresión física y el descubrimiento entre tres computadoras distintas requieren comprobación en los equipos de destino. Las pruebas automatizadas verifican la generación del PDF, los paneles y la sincronización HTTP en el mismo equipo.
