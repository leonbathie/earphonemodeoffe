package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Etat observable du forcage de routage audio.
 *
 * Les appels a AudioSystem.setForceUse etaient avales par des try/catch vides : impossible
 * de savoir s'ils prenaient effet. On enregistre ici le resultat reel pour l'afficher dans
 * la carte de diagnostic.
 */
object RoutingDiagnostics {

  var forceUse by mutableStateOf("non tente")
    private set

  /** Les 5 usages forces par l'app, avec un nom court pour l'affichage. */
  private val USES = listOf(0 to "COMM", 1 to "MEDIA", 2 to "RECORD", 4 to "SYSTEM", 7 to "RING")

  /**
   * Appelle AudioSystem.setForceUse et retourne un resume lisible du resultat.
   *
   * Deux modes d'echec sont distingues, car ils n'ont pas la meme cause :
   *  - une exception : la methode cachee est inaccessible ;
   *  - un code de retour non nul : l'appel a abouti mais le serveur audio l'a refuse,
   *    typiquement faute de la permission systeme MODIFY_AUDIO_ROUTING.
   */
  fun applyForceUse(toSpeaker: Boolean): String {
    val value = if (toSpeaker) 1 else 0
    return try {
      val audioSystemClass = Class.forName("android.media.AudioSystem")
      val setForceUseMethod =
        audioSystemClass.getMethod(
          "setForceUse",
          Int::class.javaPrimitiveType,
          Int::class.javaPrimitiveType,
        )

      val problems = mutableListOf<String>()
      for ((use, name) in USES) {
        try {
          val status = setForceUseMethod.invoke(null, use, value) as? Int
          if (status != null && status != 0) problems += "$name=refuse($status)"
        } catch (e: Exception) {
          val real = e.cause ?: e
          problems += "$name=${real.javaClass.simpleName}"
        }
      }

      if (problems.isEmpty()) "applique sans erreur"
      else "SANS EFFET -> ${problems.joinToString(", ")}"
    } catch (e: Exception) {
      "API INACCESSIBLE (${e.javaClass.simpleName})"
    }
  }

  fun record(result: String) {
    forceUse = result
  }
}
