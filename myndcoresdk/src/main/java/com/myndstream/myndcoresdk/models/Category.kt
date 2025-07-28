package models

import android.annotation.SuppressLint
import java.io.Serializable

interface ICategoryImage : Serializable {
    val id: String
    val url: String
}

interface ICategory : Serializable {
    val id: String
    val name: String
    val image: ICategoryImage?
}

@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
data class CategoryImage(
    override val id: String,
    override val url: String
) : ICategoryImage

@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
data class Category(
    override val id: String,
    override val name: String,
    override val image: CategoryImage?
) : ICategory