package com.example.tconfirmo.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.tconfirmo.ui.theme.PlusJakartaSansFamily
import com.example.tconfirmo.ui.theme.TConfirmoTheme

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF17265F),
            Color(0xFF142259),
            Color(0xFF101B49)
        )

        //colors = listOf(
        //Color(0xFF054534),
        //Color(0xFF0A6647),
        //Color(0xFF17265F)
        //)


    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        HeaderSection(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.52f),
            gradientBrush = gradientBrush
        )

        LoginFormSection(
            phone = phone,
            onPhoneChange = { phone = it },
            password = password,
            onPasswordChange = { password = it },
            passwordVisible = passwordVisible,
            onPasswordToggle = { passwordVisible = !passwordVisible },
            onLoginSuccess = onLoginSuccess,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = (-28).dp)
        )
    }
}

@Composable
private fun HeaderSection(
    modifier: Modifier,
    gradientBrush: Brush
) {
    Box(
        modifier = modifier
            .background(gradientBrush)
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 40.dp)
    ) {
        HeaderGlow(
            modifier = Modifier
                .size(176.dp)
                .align(Alignment.TopEnd)
                .offset(x = 72.dp, y = (-40).dp),
            color = Color(0xFFFFE500),
            alpha = 0.10f
        )
        HeaderGlow(
            modifier = Modifier
                .size(128.dp)
                .align(Alignment.CenterStart)
                .offset(x = (-48).dp, y = (-12).dp),
            color = Color(0xFFFFE500),
            alpha = 0.10f
        )
        HeaderGlow(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = (-16).dp),
            color = Color(0xFFFFE500),
            alpha = 0.15f
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(top = 80.dp)) {
                ConfirmoLogoMark()

                Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "BIENVENIDO A",
                color = Color(0xFFFFE500).copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.2.sp,
                fontFamily = PlusJakartaSansFamily
            )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                text = "CONFIRMO",
                color = Color.White,
                fontSize = 55.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 52.sp,
                fontFamily = PlusJakartaSansFamily
            )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                text = "Sistema de confirmacion de depositos bancarios",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                modifier = Modifier.width(240.dp)
            )
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FeatureItem("Simple", "Registro rapido")
                VerticalDivider(color = Color(0xFFFFE500).copy(alpha = 0.35f), modifier = Modifier.height(24.dp))
                FeatureItem("Agil", "Confirmacion")
                VerticalDivider(color = Color(0xFFFFE500).copy(alpha = 0.35f), modifier = Modifier.height(24.dp))
                FeatureItem("Seguro", "Proteccion de datos")
            }
        }
    }
}

@Composable
private fun HeaderGlow(
    modifier: Modifier,
    color: Color,
    alpha: Float
) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = alpha),
                    Color.Transparent
                )
            )
        )
    )
}

@Composable
private fun ConfirmoLogoMark() {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFE500).copy(alpha = 0.16f),
        border = BorderStroke(1.dp, Color(0xFFFFE500).copy(alpha = 0.45f))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val circleStroke = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round)
            val checkStroke = 2.7.dp.toPx()

            drawCircle(
                color = Color(0xFFFFE500),
                radius = 11.5.dp.toPx(),
                center = center,
                style = circleStroke
            )
            drawLine(
                color = Color(0xFFFFE500),
                start = androidx.compose.ui.geometry.Offset(
                    x = center.x - 5.5.dp.toPx(),
                    y = center.y + 0.5.dp.toPx()
                ),
                end = androidx.compose.ui.geometry.Offset(
                    x = center.x - 1.5.dp.toPx(),
                    y = center.y + 4.5.dp.toPx()
                ),
                strokeWidth = checkStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFFE500),
                start = androidx.compose.ui.geometry.Offset(
                    x = center.x - 1.5.dp.toPx(),
                    y = center.y + 4.5.dp.toPx()
                ),
                end = androidx.compose.ui.geometry.Offset(
                    x = center.x + 7.dp.toPx(),
                    y = center.y - 6.dp.toPx()
                ),
                strokeWidth = checkStroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun LoginFormSection(
    phone: String,
    onPhoneChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordToggle: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier.imePadding(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xffEDEDED)
    ) {
        Column(
            modifier = Modifier
                .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 20.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Iniciar sesion",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17265F),
                fontFamily = PlusJakartaSansFamily
            )
            Text(
                text = "Ingresa tus credenciales para continuar",
                fontSize = 12.sp,
                color = Color(0xFF17265F).copy(alpha = 0.62f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            CustomTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = "NUMERO DE TELEFONO",
                placeholder = "987 654 321",
                leadingIcon = Icons.Default.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.height(16.dp))

            CustomTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "CONTRASENA",
                placeholder = "********",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordToggle = onPasswordToggle
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onLoginSuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFE500),
                    contentColor = Color(0xFF17265F)
                )
            ) {
                Text("Ingresar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "v2.4.1 - Produccion - 2026 Confirmo",
                    fontSize = 10.sp,
                    color = Color(0xFF17265F).copy(alpha = 0.48f)
                )
            }
        }
    }
}

@Composable
private fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color(0xFFF6F7FB),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = Color(0xFF17265F),
            unfocusedTextColor = Color(0xFF17265F),
            cursorColor = Color(0xFF17265F)
        ),
        leadingIcon = {
            Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF17265F).copy(alpha = 0.68f))
        },
        trailingIcon = if (isPassword && onPasswordToggle != null) {
            {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF17265F).copy(alpha = 0.68f)
                    )
                }
            }
        } else {
            null
        },
        label = {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17265F).copy(alpha = 0.68f),
                letterSpacing = 0.5.sp
            )
        },
        placeholder = {
            Text(text = placeholder, fontSize = 14.sp, color = Color(0xFF17265F).copy(alpha = 0.38f))
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        singleLine = true
    )
}

@Composable
private fun FeatureItem(value: String, label: String) {
    Column {
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 17.sp,
            fontFamily = PlusJakartaSansFamily
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = PlusJakartaSansFamily
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    TConfirmoTheme {
        LoginScreen(onLoginSuccess = {})
    }
}
