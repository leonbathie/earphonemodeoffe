package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Etat observable du forcage de routage audio.
 *
 * Les appels a AudioSystem.setForceUse etaient avales par des try/catch vides : impossible
 * de savoir s'ils prenaient effet. On enregistre ici le resultat reel, pour l'afficher et
 * pour arreter de marteler un appel que le systeme refuse.
 */
object RoutingDiagnostics {

  /** Resume lisible du dernier appel, affiche dans la carte de diagnostic. */
  var forceUse by mutableStateOf("non tente")
    private set

  /**
   * Vrai quand le systeme a refuse le forcage. L'UI s'en sert pour dire la verite a
   * l'utilisateur, et le service pour cesser de reessayer inutilement.
   */
  var forcingRefused by mutableStateOf(false)
    private set

  /** Les 5 usages forces par l'app, avec un nom court pour l'affichage. */
  private val USES = listOf(0 to "COMM", 1 to "MEDIA", 2 to "RECORD", 4 to "SYSTEM", 7 to "RING")

  /**
   * Appelle AudioSystem.setForceUse et met a jour l'etat observable.
   *
   * Deux modes d'echec sont distingues, car ils n'ont pas la meme cause :
   *  - une exception : la methode cachee est inaccessible ;
   *  - un code de retour non nul : l'appel a abouti mais le serveur audio l'a refuse,
   *    faute de la permission systeme MODIFY_AUDIO_ROUTING.
   */
  fun applyForceUse(toSpeaker: Boolean) {
    val value = if (toSpeaker) 1 else 0

    val audioSystemClass =
      try {
        Class.forName("android.media.AudioSystem")
      } catch (e: Exception) {
        forceUse = "API INACCESSIBLE (${e.javaClass.simpleName})"
        forcingRefused = true
        return
      }

    val setForceUseMethod =
      try {
        audioSystemClass.getMethod(
          "setForceUse",
          Int::class.javaPrimitiveType,
          Int::class.javaPrimitiveType,
        )
      } catch (e: Exception) {
        forceUse = "API INACCESSIBLE (${e.javaClass.simpleName})"
        forcingRefused = true
        return
      }

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

    if (problems.isEmpty()) {
      forceUse = "applique sans erreur"
      forcingRefused = false
    } else {
      forceUse = "SANS EFFET -> ${problems.joinToString(", ")}"
      forcingRefused = true
    }
  }
}
