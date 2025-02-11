package com.pascaldornfeld.gsdble.file_dumping

import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import com.google.gson.GsonBuilder
import com.pascaldornfeld.gsdble.preprocessing.PreprocessedData
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

object FileOperations {
    private val gson = GsonBuilder().create()
    var lastFile : File? = null

    /**
     * Write raw and meta data of a gesture to a JSON file
     * and save it in the user's directory in background.
     *
     * @param gestureData The class which contains all raw and meta data of the gesture
     * @param subFolder Name of a subfolder to save the recording in. If null, save in default folder
     */
    fun writeGestureFile(gestureData: GestureData, subFolder: String? = null) {
        AsyncTask.execute {
            // create filename
            synchronized(FileOperations) {
                var fileNamePostfix = 2
                var recordingName = gestureData.label.toString()  + gestureData.startTime.toString()
                if (subFolder != null) {
                    recordingName = "$subFolder${File.separator}$recordingName"
                }
                var file = getFileFromPrefixAndCreateParent(recordingName)
                while (file.exists()) {
                    file = getFileFromPrefixAndCreateParent(
                        "${gestureData.startTime.toString()}_$fileNamePostfix"
                    )
                    fileNamePostfix++
                }
                file
            }.let { file: File ->
                lastFile = file
                // write file

                Log.d("FILE_DUMP", "Gesture Dump Start")
                FileWriter(file, false).use { fileWriter: FileWriter ->
                    BufferedWriter(fileWriter).use { bufferedWriter: BufferedWriter ->
                        bufferedWriter.write(gson.toJson(gestureData))
                        bufferedWriter.newLine()
                    }
                }
                Log.d("FILE_DUMP", "Gesture Dump End")
            }

        }
    }

    /**
     * Return the number of already saved gesture files in the user's directory.
     *
     * @param user The current user name
     * @return Number of existing gesture files
     */
    @Suppress("unused")
    private fun getNewFileCount(user: String): Int =
        File("${getGesturesFolderPath()}$user").listFiles()?.size ?: 0

    /**
     * Check if the desired directory path exists, if not it is created.
     *
     * @param prefix The file name consists of data and startTime of the gesture
     * @return The file which will be written to.
     */
    private fun getFileFromPrefixAndCreateParent(prefix: String): File =
        File("${getGesturesFolderPath()}${File.separator}$prefix.json")
            .apply { if (!parentFile.exists()) parentFile.mkdirs() }

    private fun getGesturesFolderPath(): String =
        "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}GSD_Recordings${File.separator}"


    /**
     * Write preprocessed gesture data to a JSON file
     * and save it in the user's directory in background.
     *
     * @param preprocessedData The class which contains all preprocessed data of the gesture
     * @param subFolder Name of a subfolder to save the recording in. If null, save in default folder
     */
    fun writePreprocessedFile(preprocessedData: PreprocessedData, subFolder: String? = null) {
        AsyncTask.execute {
            // create filename
            synchronized(FileOperations) {
                var fileNamePostfix = 2
                var recordingName = preprocessedData.label.toString()  + preprocessedData.startTime.toString() + "_PreProcessed"
                if (subFolder != null) {
                    recordingName = "$subFolder${File.separator}$recordingName"
                }
                var file = getFileFromPrefixAndCreateParent(recordingName)
                while (file.exists()) {
                    file = getFileFromPrefixAndCreateParent(
                        "${preprocessedData.startTime.toString()}_$fileNamePostfix"
                    )
                    fileNamePostfix++
                }
                file
            }.let { file: File ->
                lastFile = file
                // write file
                Log.d("FILE_DUMP", "Processed Gesture Dump Start")
                FileWriter(file, false).use { fileWriter: FileWriter ->
                    BufferedWriter(fileWriter).use { bufferedWriter: BufferedWriter ->
                        bufferedWriter.write(gson.toJson(preprocessedData))
                        bufferedWriter.newLine()
                    }
                }
                Log.d("FILE_DUMP", "Processed Gesture Dump End")
            }

        }
    }

}