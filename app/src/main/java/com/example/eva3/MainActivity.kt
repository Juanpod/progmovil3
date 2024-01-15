@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.eva3

import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role.Companion.Image
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.engage.common.datamodel.Image
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.MapView
import java.io.File
import java.text.Normalizer.Form
import java.time.LocalDateTime

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

enum class Pantalla{
    FORM,
    FOTO,
    MAPA
}

class AppVM : ViewModel(){
    val pantallaActual = mutableStateOf(Pantalla.FORM)
    // callbacks
    var onPermisoCamaraOk:() -> Unit = {}
    var onPermisoUbicacionOk:() -> Unit = {}

    var lanzadorPermisos:ActivityResultLauncher<Array<String>>? = null

    fun cambiarPantallaFoto() { pantallaActual.value = Pantalla.FOTO}
    fun cambiarPantallaForm() { pantallaActual.value = Pantalla.FORM}
    fun cambiarPantallaMapa() { pantallaActual.value = Pantalla.MAPA}
}

class FormRegistroVM : ViewModel() {
    val nombre = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val foto = mutableStateOf<Uri?>(null)

}


class MainActivity : ComponentActivity() {
    val camaraVM: AppVM by viewModels()
    lateinit var cameraController:LifecycleCameraController
    val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        when {
            (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false)
                    or (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) -> {
                Log.v("callback RequestMultiplePermissions", "permiso ubicacion granted")
                camaraVM.onPermisoUbicacionOk()
            }
            (it[android.Manifest.permission.CAMERA] ?: false) -> {
                Log.v("callback RequestMultiplePermissions", "permiso camara granted")
                camaraVM.onPermisoCamaraOk()
            }
            else -> {
                Log.v("lanzador permisos callback" , "Sin permisos")
            }
        }
    }
    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        camaraVM.lanzadorPermisos = lanzadorPermisos
        setupCamara()
        setContent {
            AppUI(cameraController)
        }
    }
}

fun generarNombreSegunFechaHastaSegundo():String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0,14)

fun crearArchivoImagenPrivado(contexto:Context):File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)

fun uri2imageBitmap(uri:Uri, contexto: Context) = BitmapFactory.decodeStream(
    contexto.contentResolver.openInputStream(uri)
).asImageBitmap()


fun capturarFotografia(
    cameraController: LifecycleCameraController,
    archivo: File,
    contexto: Context,
    onImagenGuardada: (uri: Uri) -> Unit)
{
    val opciones = OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object: OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.also {// also era let
                    Log.v("tomarFotografia()::onImageSaved", "Foto guardada en ${it.toString()}")
                    guardarImagenMediaStore(contexto, it)
                    onImagenGuardada(it)
                }
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("capturarFotografia::OnImageSavedCallback::onError", exception.message?:"Error")
            }

        }
    )

}

private fun guardarImagenMediaStore(context: Context, imageUri: Uri) {
    try {
        val contentResolver = context.contentResolver


        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Imagen_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.DATA, imageUri.path)
        }

        val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val insertedUri = contentResolver.insert(contentUri, contentValues)

        Log.v("guardarImagenMediaStore", "Imagen guardada en MediaStore: $insertedUri")
    } catch (e: Exception) {
        Log.e("guardarImagenMediaStore", "Error al guardar la imagen en MediaStore: ${e.message}")
    }
}


class SinPermisoException(mensaje:String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOk: (location:Location) -> Unit):Unit {
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e:SecurityException) {
        throw SinPermisoException(e.message?:"No tiene permisos para conseguir ubicacion")
    }
}


@Composable
fun AppUI(cameraController: LifecycleCameraController) {
    val contexto = LocalContext.current
    val formRegistroVM:FormRegistroVM = viewModel()
    val appVM:AppVM =viewModel()
    when(appVM.pantallaActual.value) {
        Pantalla.FORM -> {
            PantallaFormUI(
                formRegistroVM,
                tomarFotoOnClick = {
                    appVM.cambiarPantallaFoto()
                    appVM.lanzadorPermisos?.launch(arrayOf(Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    appVM.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            formRegistroVM.latitud.value = it.latitude
                            formRegistroVM.longitud.value = it.longitude
                        }
                    }
                    appVM.lanzadorPermisos?.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            )
        }
        Pantalla.FOTO -> {
            PantallaCamaraUI(FormRegistroVM(), appVM, cameraController)
        }
        Pantalla.MAPA -> {
            MapaFullScreenUI(
                latitud = formRegistroVM.latitud.value,
                longitud = formRegistroVM.longitud.value,
                salirMapa = {
                    appVM.cambiarPantallaForm()
                }
            )
        }
        else -> {
            Log.v("AppUI()", "NO DEBERIA ESTAR AQUI")
        }
    }

}
@Composable
fun MapaFullScreenUI(latitud: Double, longitud: Double, salirMapa: () -> Unit) {

    MapaOsmUI(latitud = latitud, longitud = longitud)

    Button(
        onClick = { salirMapa() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Salir del Mapa")
    }
}



@Composable
fun PantallaFormUI(formRegistroVM: FormRegistroVM, tomarFotoOnClick:() -> Unit = {}, actualizarUbicacionOnClick:() -> Unit = {})
{
    val contexto = LocalContext.current
    val appVM:AppVM =viewModel()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            label = {Text("Nombre del lugar visitado")},
            value = formRegistroVM.nombre.value,
            onValueChange = {formRegistroVM.nombre.value = it},
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )
        Text("Agregar fotografia del lugar visitado")
        Button(onClick = {
            tomarFotoOnClick()


        }) {
            Text("Tomar foto")
        }



        Text("La ubicacion es: lat: ${formRegistroVM.latitud.value} y long: ${formRegistroVM.longitud.value}")

        Button(onClick = {
            actualizarUbicacionOnClick()
        }) {
            Text("Actualiza Ubicacion")
        }

        Button(onClick = {

            appVM.cambiarPantallaMapa()
        }) {
            Text("Ver Mapa en Pantalla Completa")
        }
        Spacer(Modifier.height(100.dp))

        formRegistroVM.foto.value?.let {
            Box(Modifier.size(200.dp, 100.dp)) {
                Image(
                    painter = BitmapPainter(uri2imageBitmap(it, contexto)),
                    contentDescription = "Imagen capturada"
                )
            }

        }
    }
}

@Composable
fun PantallaCamaraUI(formRegistroVM: FormRegistroVM, appVM: AppVM, cameraController: LifecycleCameraController){

    val contexto = LocalContext.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
           PreviewView(it).apply {
               controller = cameraController
           }
        }
    )
    Button(onClick = {
        capturarFotografia(
            cameraController,
            crearArchivoImagenPrivado(contexto),
            contexto
        ) {
            formRegistroVM.foto.value = it

            appVM.cambiarPantallaForm()
        }
    }) {
        Text ("Captura Foto")
    }
}

@Composable
fun MapaOsmUI(latitud:Double, longitud:Double) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            org.osmdroid.views.MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                org.osmdroid.config.Configuration.getInstance().userAgentValue = contexto.packageName
            }
        },
        update = {
            it.overlays.removeIf{true}
            it.invalidate()
            it.controller.setZoom(18.0)
            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)

            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )

}

