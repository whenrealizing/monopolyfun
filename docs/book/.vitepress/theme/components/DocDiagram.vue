<script setup>
import { computed } from "vue";
import { diagrams } from "../diagram-data.mjs";

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
});

const diagram = computed(() => diagrams[props.id]);
const isZh = computed(() => props.id.startsWith("zh-"));

// 中文注释：系统闭环按泳道拆分，突出写入、事实源、读回三类职责，避免流程卡片挤在同一平面。
const closedLoopModel = computed(() => {
  if (!diagram.value || diagram.value.layout !== "closed-loop") return null;
  return {
    write: [0, 1, 2, 3, 4].map(stepAt),
    fact: stepAt(5),
    read: [
      {
        label: "Web UI / Agent",
        sub: "readback",
        number: "R",
      },
      stepAt(6),
      stepAt(0),
    ],
    labels: {
      write: isZh.value ? "1 写入：目标变成验收结果" : "1 Write: goal becomes acceptance",
      fact: isZh.value ? "2 保存：只认这一份确认状态" : "2 Store: one confirmed state",
      read: isZh.value ? "3 读回：页面和 Agent 继续工作" : "3 Readback: UI and agents continue",
      factTitle: isZh.value ? "API + Database" : "API + Database",
      factNote: isZh.value ? "所有写入先落到这里；页面、接口和 Agent 都读取这里确认后的状态。" : "All writes land here first; pages, APIs, and agents read this confirmed state.",
    },
  };
});

// 中文注释：序列图只保存 actor 下标，渲染时统一解析，避免文档页重复维护参与方文案。
function actorName(index) {
  return diagram.value.actors[index];
}

function stepAt(index) {
  return {
    label: diagram.value.labels[index],
    sub: diagram.value.subs?.[index],
    number: String(index + 1).padStart(2, "0"),
  };
}
</script>

<template>
  <figure
    v-if="diagram"
    class="doc-diagram"
    :class="[`doc-diagram--${diagram.type}`, diagram.layout ? `doc-diagram--${diagram.layout}` : '', { 'doc-diagram--loop': diagram.loop }]"
  >
    <ol v-if="diagram.type === 'flow' && diagram.layout === 'reader-rail'" class="doc-reader-rail">
      <li v-for="(label, index) in diagram.labels" :key="`${props.id}-${label}`">
        <span>{{ String(index + 1).padStart(2, "0") }}</span>
        <strong>{{ label }}</strong>
        <em v-if="diagram.subs?.[index]">{{ diagram.subs[index] }}</em>
      </li>
    </ol>

    <div v-else-if="diagram.type === 'flow' && diagram.layout === 'closed-loop' && closedLoopModel" class="doc-closed-loop">
      <section class="doc-closed-loop-row doc-closed-loop-row--write">
        <h3>{{ closedLoopModel.labels.write }}</h3>
        <ol>
          <li v-for="step in closedLoopModel.write" :key="`${props.id}-write-${step.number}`">
            <span>{{ step.number }}</span>
            <strong>{{ step.label }}</strong>
            <em>{{ step.sub }}</em>
          </li>
        </ol>
      </section>

      <section class="doc-closed-loop-row doc-closed-loop-row--fact">
        <h3>{{ closedLoopModel.labels.fact }}</h3>
        <div class="doc-closed-loop-fact">
          <span>{{ closedLoopModel.fact.number }}</span>
          <strong>{{ closedLoopModel.labels.factTitle }}</strong>
          <em>{{ closedLoopModel.fact.label }} / {{ closedLoopModel.fact.sub }}</em>
          <p>{{ closedLoopModel.labels.factNote }}</p>
        </div>
      </section>

      <section class="doc-closed-loop-row doc-closed-loop-row--read">
        <h3>{{ closedLoopModel.labels.read }}</h3>
        <ol>
          <li v-for="step in closedLoopModel.read" :key="`${props.id}-read-${step.number}-${step.label}`">
            <span>{{ step.number }}</span>
            <strong>{{ step.label }}</strong>
            <em>{{ step.sub }}</em>
          </li>
        </ol>
      </section>
    </div>

    <ol v-else-if="diagram.type === 'flow'" class="doc-diagram-flow">
      <li
        v-for="(label, index) in diagram.labels"
        :key="`${props.id}-${label}`"
        class="doc-diagram-card"
        :class="{ 'is-core': diagram.loop && index === diagram.labels.length - 2 }"
      >
        <span class="doc-diagram-index">{{ String(index + 1).padStart(2, "0") }}</span>
        <strong>{{ label }}</strong>
        <span v-if="diagram.subs?.[index]">{{ diagram.subs[index] }}</span>
      </li>
    </ol>

    <div v-else-if="diagram.type === 'hub'" class="doc-diagram-hub">
      <div class="doc-diagram-hub-center">
        <span>Core</span>
        <strong>{{ diagram.center }}</strong>
      </div>
      <div class="doc-diagram-hub-items">
        <div v-for="(item, index) in diagram.items" :key="`${props.id}-${item}`" class="doc-diagram-card">
          <span class="doc-diagram-index">{{ String(index + 1).padStart(2, "0") }}</span>
          <strong>{{ item }}</strong>
        </div>
      </div>
    </div>

    <div v-else-if="diagram.type === 'sequence'" class="doc-diagram-sequence">
      <div class="doc-diagram-actors">
        <span v-for="actor in diagram.actors" :key="`${props.id}-${actor}`">{{ actor }}</span>
      </div>
      <ol class="doc-diagram-sequence-steps">
        <li v-for="(step, index) in diagram.steps" :key="`${props.id}-${index}`">
          <span>{{ actorName(step[0]) }}</span>
          <strong>{{ step[2] }}</strong>
          <span>{{ actorName(step[1]) }}</span>
        </li>
      </ol>
    </div>

    <div v-else-if="diagram.type === 'architecture'" class="doc-diagram-architecture">
      <section v-for="layer in diagram.layers" :key="`${props.id}-${layer[0]}`" class="doc-diagram-layer">
        <h3>{{ layer[0] }}</h3>
        <div>
          <span v-for="node in layer[1]" :key="`${props.id}-${layer[0]}-${node}`">{{ node }}</span>
        </div>
      </section>
    </div>

    <figcaption v-if="diagram.legend?.length" class="doc-diagram-legend">
      <span v-for="item in diagram.legend" :key="`${props.id}-${item}`">{{ item }}</span>
    </figcaption>
  </figure>
</template>
