precision mediump float;       	// Set the default precision to medium. We don't need as high of a 
								// precision in the fragment shader.
uniform vec3 u_LightPos;       	// The position of the light in eye space.
uniform vec3 u_LightPos2; 
uniform sampler2D u_Texture;    // The input texture.
  
varying vec3 v_Position;		// Interpolated position for this fragment.
varying vec4 v_Color;          	// This is the color from the vertex shader interpolated across the 
  								// triangle per fragment.
varying vec3 v_Normal;         	// Interpolated normal for this fragment.
varying vec2 v_TexCoordinate;   // Interpolated texture coordinate per fragment.
  
// The entry point for our fragment shader.
void main()                    		
{                              
	// Will be used for attenuation.
    float distance = length(u_LightPos - v_Position);                  
	float distance2 = length(u_LightPos2 - v_Position);
	
	// Get a lighting direction vector from the light to the vertex.
    vec3 lightVector = normalize(u_LightPos - v_Position);              	
    vec3 lightVector2 = normalize(u_LightPos2 - v_Position);

	// Calculate the dot product of the light vector and vertex normal. If the normal and light vector are
	// pointing in the same direction then it will get max illumination.
    float diffuse = max(dot(v_Normal, lightVector), 0.0);               	  		  													  
	float diffuse2 = max(dot(v_Normal, lightVector2), 0.0);

	// Add attenuation. 
    //diffuse = diffuse * (1.0 / (1.0 + (0.10 * distance)));
    
    // Add ambient lighting
    diffuse = diffuse + diffuse2;  

	// Multiply the color by the diffuse illumination level and texture value to get final output color.
    gl_FragColor = (v_Color * diffuse * texture2D(u_Texture, v_TexCoordinate));                                  		
}                                                                     	