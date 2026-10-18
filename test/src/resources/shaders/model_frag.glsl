#version 330 core
out vec4 FragColor;

in vec3 FragPos;
in vec3 Normal;
in vec2 TexCoord;

uniform sampler2D texture_diffuse1;
uniform vec4 materialColor;
uniform bool hasTexture;
uniform vec3 lightPos;
uniform bool isOutlinePass;

void main() {
    vec4 texColor = vec4(1.0);
    if (hasTexture) {
        texColor = texture(texture_diffuse1, vec2(TexCoord.x, 1.0 - TexCoord.y));
    }
    vec4 baseColor = materialColor * texColor;

    if (baseColor.a < 0.1) {
        discard;
    }

    if (isOutlinePass) {
        FragColor = vec4(0.1, 0.1, 0.1, 1.0); 
        return;
    }

    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);
    float NdotL = dot(norm, lightDir);
    
    float intensity = NdotL > 0.1 ? 1.0 : 0.6; 
    vec3 viewDir = normalize(-FragPos);
    float rim = 1.0 - max(dot(viewDir, norm), 0.0);
    intensity += pow(rim, 3.0) * 0.3;

    vec3 finalColor = baseColor.rgb * intensity;
    FragColor = vec4(finalColor, baseColor.a);
}