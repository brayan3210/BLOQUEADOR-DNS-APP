package com.brayan.filtrocontenido

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla que se muestra cuando el guardian de accesibilidad detecta una
 * BUSQUEDA explicita. Cubre la pantalla para que los resultados no se vean, y
 * el unico camino es "Entendido" -> inicio. No usa SafeSearch: es autocontrol.
 */
class BloqueoBusquedaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val navy = Color.parseColor("#0b1220")
        val gold = Color.parseColor("#e6b955")
        val txt = Color.parseColor("#e8edf7")
        val soft = Color.parseColor("#9fb0cc")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(navy)
            setPadding(64, 64, 64, 64)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(TextView(this).apply {
            text = "🛡️"
            textSize = 56f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Búsqueda bloqueada"
            textSize = 24f
            setTextColor(gold)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        })
        root.addView(TextView(this).apply {
            text = "Esta búsqueda contiene contenido explícito y fue detenida por " +
                "tu filtro de autocontrol. No es un castigo: es la barrera que tú " +
                "mismo pediste poner."
            textSize = 15f
            setTextColor(txt)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "“Un momento de fricción hoy vale más que un arrepentimiento mañana.”"
            textSize = 14f
            setTextColor(soft)
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        })
        root.addView(Button(this).apply {
            text = "Entendido"
            setOnClickListener { goHome() }
        })

        setContentView(root)
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    @Deprecated("Ir a inicio en vez de volver a los resultados")
    override fun onBackPressed() {
        goHome()
    }
}
