package com.example.allaboutmusic.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.NSObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (String?) -> Unit
): ImagePickerLauncher {
    val launcher = remember(onImagePicked) {
        ImagePickerLauncher(
            launch = {
                val configuration = PHPickerConfiguration()
                configuration.filter = PHPickerFilter.imagesFilter
                configuration.selectionLimit = 1

                val picker = PHPickerViewController(configuration = configuration)
                val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                    override fun picker(
                        picker: PHPickerViewController,
                        didFinishPicking: List<*>
                    ) {
                        picker.dismissViewControllerAnimated(true, null)

                        val result = didFinishPicking.firstOrNull() as? PHPickerResult
                        if (result == null) {
                            onImagePicked(null)
                            return
                        }

                        result.itemProvider.loadDataRepresentationForTypeIdentifier(
                            typeIdentifier = "public.image"
                        ) { data, _ ->
                            if (data == null) {
                                onImagePicked(null)
                                return@loadDataRepresentationForTypeIdentifier
                            }

                            val image = UIImage(data = data)
                            val jpegData = UIImageJPEGRepresentation(image, 0.85)
                            if (jpegData == null) {
                                onImagePicked(null)
                                return@loadDataRepresentationForTypeIdentifier
                            }

                            val fileManager = NSFileManager.defaultManager
                            val documentsUrl = fileManager.URLsForDirectory(
                                NSDocumentDirectory,
                                NSUserDomainMask
                            ).firstOrNull() as? NSURL

                            val coversDir = documentsUrl?.URLByAppendingPathComponent("mix_covers")
                            coversDir?.path?.let { fileManager.createDirectoryAtPath(it, true, null, null) }

                            val fileName = "${Uuid.random()}.jpg"
                            val filePath = coversDir?.URLByAppendingPathComponent(fileName)?.path

                            if (filePath != null) {
                                jpegData.writeToFile(filePath, true)
                                onImagePicked(filePath)
                            } else {
                                onImagePicked(null)
                            }
                        }
                    }
                }
                picker.delegate = delegate

                UIApplication.sharedApplication.keyWindow?.rootViewController
                    ?.presentViewController(picker, animated = true, completion = null)
            }
        )
    }
    return launcher
}
