#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>
#include <minecraft:globals.glsl>
#include <minecraft:light.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <simplelod:transform.glsl>
#ifndef MULTIDRAW_LOD
#include <simplelod:lodchunk.glsl>
#endif

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec3 Normal;
#ifdef MULTIDRAW_LOD
layout(location = 3) in ivec3 ChunkPosition;
#endif

uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 relPos;

void main() {
    vec3 pos = Position + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vec4 litColor = Color * sample_lightmap(Sampler2, ivec2(LOD_BLOCK_LIGHT, LOD_SKY_LIGHT));
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, litColor);

    if (Normal.y != 0.0) {
        relPos = abs(Position.xz);
    } else if (Normal.x != 0.0) {
        relPos = abs(Position.zy);
    } else {
        relPos = abs(Position.xy);
    }
}
