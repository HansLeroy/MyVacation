package com.example.myvacation

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.time.LocalDateTime


enum class Pantalla {
    FORM, FOTO
}

//define un viewmodel para gestionar el estado de la aplicacion
class CameraAppViewModel : ViewModel() {
    //variable para controlar la pantalla actual de la aplicacion
    val pantalla = mutableStateOf(Pantalla.FORM)

    var onPermisoCamaraOk: () -> Unit = {}
    var onPermisoUbicacionOk: () -> Unit = {}

    var lanzadorPermisos: ActivityResultLauncher<Array<String>>? = null

    //funcion para cambiar la pantalla a la foto
    fun cambiarPantallaFoto() {
        pantalla.value = Pantalla.FOTO
    }

    //funcion para cambiar la pantalla a la vista de formulario
    fun cambiarPantallaForm() {
        pantalla.value = Pantalla.FORM
    }
}
class AppVM: ViewModel(){
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    var permisosUbicacionOk:()-> Unit ={}
}

class FormLugarViewModel : ViewModel() {
    val lugar = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val fotoLugar = mutableStateOf<Uri?>(null)

}

class MainActivity : ComponentActivity() {
    val cameraAppVm: CameraAppViewModel by viewModels()
    val appVM:AppVM by viewModels()
    lateinit var cameraController: LifecycleCameraController
    val lanzadorPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[android.Manifest.permission.ACCESS_FINE_LOCATION]
                    ?: false) or (it[android.Manifest.permission.ACCESS_COARSE_LOCATION]
                    ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicación granted")
                    cameraAppVm.onPermisoUbicacionOk()
                }

                (it[android.Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso cámara granted")
                    cameraAppVm.onPermisoCamaraOk()
                }

                else -> {
                }

            }
        }

    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector =
            CameraSelector.DEFAULT_BACK_CAMERA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}


fun generarNombreSegunFechaHastaSegundo(): String =
    LocalDateTime.now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)

fun crearArchivoImagenPrivado(contexto: Context): File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)


fun uri2imageBitmap(uri: Uri, contexto: Context) = BitmapFactory.decodeStream(
    contexto.contentResolver.openInputStream(uri)
).asImageBitmap()

fun tomarFoto(
    cameraController: CameraController,
    archivo: File,
    contexto: Context,
    imagenGuardadaOk: (uri: Uri) -> Unit
) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(outputFileOptions,
        ContextCompat.getMainExecutor(contexto),
        object : OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.also {
                    Log.v("tomarFoto()::onImageSaved", "foto guardad en ${it.toString()}")
                    imagenGuardadaOk(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("tomar Foto()", "error:${exception.message}")
            }
        })
}

class SinPermisoException(mensaje: String) : Exception(mensaje)

fun getUbicacion(
    contexto: Context, onUbicacionOk: (location: Location) -> Unit
): Unit {
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e: SecurityException) {
        throw SinPermisoException(e.message ?: "no tiene permiso para la ubicacion")
    }
}

@Composable
fun AppUI(
    cameraController: CameraController) {
    val contexto = LocalContext.current
    val formLugarVm: FormLugarViewModel = viewModel()
    val cameraAppViewModel: CameraAppViewModel = viewModel()

    when (cameraAppViewModel.pantalla.value) {
        Pantalla.FORM -> {
            PantallaFormUI(
                formLugarVm,
                tomarFotoOnClick = {
                    cameraAppViewModel.cambiarPantallaFoto()

                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(Manifest.permission.CAMERA))
                }, actualizarUbicacionOnClick = {
                    cameraAppViewModel.onPermisoCamaraOk = {
                        getUbicacion(contexto) {
                            formLugarVm.latitud.value = it.latitude
                            formLugarVm.longitud.value = it.latitude
                        }
                    }
                    cameraAppViewModel.lanzadorPermisos?.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    )
                }

        Pantalla.FOTO -> {
            PantallaFotoUI(formLugarVm, cameraAppViewModel, cameraController)
        }
        else -> {
            Log.v("AppUI()", "when no debería entrar aquí")
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormUI(
    formLugarVm: FormLugarViewModel,
    tomarFotoOnClick: () -> Unit = {},
    actualizarUbicacionOnClick: () -> Unit = {},

    ) {
    var showDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null)}
    val contexto = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Campo de texto para el nombre del lugar
        TextField(
            label = { Text("Lugar en que estas") },
            value = formLugarVm.lugar.value,
            onValueChange = {formLugarVm.lugar.value = it},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)

        )

        Text("Foto del lugar de tus vacaciones: ")
        Button(
            onClick = {
                tomarFotoOnClick()
            }
        ) {
            Text("Tomar fotografía")
        }
        formLugarVm.fotoLugar.value?.also { fotoUri ->
            Box(
                Modifier.size(200.dp, 100.dp)
            ) {
                Image(
                    painter = BitmapPainter(uri2imageBitmap(fotoUri, contexto)),
                    contentDescription = "imagen de tus vacaciones${formLugarVm.lugar.value}",
                    modifier = Modifier.clickable {
                        selectedImageUri = formLugarVm.fotoLugar.value
                        showDialog = true
                    }
                )
            }
        }
        Text("La ubicación es: lat:${formLugarVm.latitud.value} y long:${formLugarVm.longitud.value}")
        Button(
            onClick = {
                actualizarUbicacionOnClick()
            }
        ) {
            Text("Actualizar Ubicación")
        }
        Spacer(Modifier.height(100.dp))
        MapaOsmUI(
            formLugarVm.latitud.value, formLugarVm.longitud.value
        )
    }
}
fun conseguirUbicacion(contexto: Context, onSuccess:(ubicacion: Location)-> Unit){
    try{
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )
        tarea.addOnSuccessListener {
            onSuccess(it)
        }
    }catch(se:SecurityException){
        throw FaltaPermisosException("sin permisos de ubicacion")
    }

}

class FaltaPermisosException(mensaje:String): Exception(mensaje)
@Composable
fun PantallaFotoUI(
    formLugarVm: FormLugarViewModel,
    appViewModel: CameraAppViewModel,
    cameraController: CameraController
) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        }, modifier = Modifier.fillMaxSize()
    )

    Button(onClick = {
        tomarFoto(
            cameraController,
            crearArchivoImagenPrivado(contexto),
            contexto
        ) {
            formLugarVm.fotoLugar.value = it
            appViewModel.cambiarPantallaForm()
        }
    }) {
        Text("Tomar foto")
    }
}

@Composable
fun MapaOsmUI(latitud: Double, longitud: Double) {
    val contexto = LocalContext.current

    AndroidView(factory = {
        MapView(it).also {
            it.setTileSource(TileSourceFactory.MAPNIK)
            org.osmdroid.config.Configuration.getInstance().userAgentValue = contexto.packageName
        }
    }, update = {
        it.overlays.removeIf { true }
        it.invalidate()
        it.controller.setZoom(18.0)

        val geoPoint = GeoPoint(latitud, longitud)
        it.controller.animateTo(geoPoint)

        val marcador = org.osmdroid.views.overlay.Marker(it)
        marcador.position = geoPoint
        marcador.setAnchor(
            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
            org.osmdroid.views.overlay.Marker.ANCHOR_CENTER
        )
        it.overlays.add(marcador)
    }
    )
}

