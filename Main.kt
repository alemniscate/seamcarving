package seamcarving

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.*
import kotlin.math.sqrt
import java.util.PriorityQueue

fun main(args: Array<String>) {
    val types = ImageIO.getReaderMIMETypes()
    for (type in types) {
        println(type)
    }

    var inFileName = ""
    var outFileName = ""
    var widthReduce = 0
    var heightReduce = 0
    for (i in args.indices) {
        if ("-in".equals(args[i])) {
            inFileName = args[i + 1]
            continue
        }
        if ("-out".equals(args[i])) {
            outFileName = args[i + 1]
            continue
        }
        if ("-width".equals(args[i])) {
            widthReduce = args[i + 1].toInt()
            continue
        }
        if ("-height".equals(args[i])) {
            heightReduce = args[i + 1].toInt()
            continue
        }
    }

    if ("".equals(inFileName)) {
        return
    }

    if ("".equals(outFileName)) {
        return
    }

    val file = File(inFileName)
    println(file.absolutePath)
    if (!file.exists()) {
        println("file not found")
        return
    }

    val bi = ImageIO.read(File(inFileName))
    if (bi == null) {
        println("ImageIO read error")
        println(inFileName)
        return
    }

    var width = bi.width
    var height = bi.height
    var px = bi.getRGB(0, 0, width, height, null, 0, width)

    val energy = Energy(px, width, height)
    println("edges build complete")

    var edges = energy.edges

    repeat(widthReduce) {
        val vr = VerticalSeam(edges, width, height)
        width--
        val dpx = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).getRGB(0, 0, width, height, null, 0, width)
        val dedges = DoubleArray(width * height)
        vr.reduce(px, edges, dpx, dedges)
        px = dpx
        edges = dedges
    }

    println("vertical complete")

    repeat(heightReduce) {
        val hr = HorizontalSeam(edges, width, height)
        height--
        val dpx = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).getRGB(0, 0, width, height, null, 0, width)
        val dedges = DoubleArray(width * height)
        hr.reduce(px, edges, dpx, dedges)
        px = dpx
        edges = dedges
    }

    println("horizontal complete")

    val bio = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    bio.setRGB(0, 0, width, height, px, 0, width)

    ImageIO.write(bio, "png", File(outFileName))

}

fun toRed(i: Int, px: IntArray) {

    val argb = px[i]
    var a = argb shr 24 and 0xff
    var r = 255
    var g = 0
    var b = 0

    px[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
}

class Energy(px: IntArray, w: Int, h: Int) {
    val edges = DoubleArray(w * h)

    init {
        for (i in px.indices) {
            edges[i] = getEnergy(i, w, h, px)
        }
    }

    fun getEnergy(i: Int, w: Int, h: Int, spx: IntArray): Double {

        var i1 = i
        var i2 = i
        if (i % w == 0) {
            i1++
        }
        if (i % w == w - 1) {
            i1--
        }
        if (i / w == 0) {
            i2 += w
        }
        if (i / w == h - 1) {
            i2 -= w
        }
        val xGrad = getGrad(spx[i1 + 1], spx[i1 - 1])
        val yGrad = getGrad(spx[i2 + w], spx[i2 - w])
        val energy = sqrt((xGrad + yGrad).toDouble())

        return energy
    }

    fun getGrad(argb1: Int, argb2: Int): Int {
        var r1 = argb1 shr 16 and 0xff
        var g1 = argb1 shr 8 and 0xff
        var b1 = argb1 and 0xff

        var r2 = argb2 shr 16 and 0xff
        var g2 = argb2 shr 8 and 0xff
        var b2 = argb2 and 0xff

        return (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2)
    }
}

class VerticalSeam(val edges: DoubleArray, val w: Int, val h: Int) {

    val indexList: IntArray

    init {
        val nodes = Array<Node>(w * (h + 1)) { it ->
            val x = it % w
            val y = it / w
            var distance = Double.MAX_VALUE
            if (it < w) {
                distance = 0.0
            }
            Node(distance, false, -1, it, x, y)
        }

        val dik = VerticalDijkstra(nodes, edges, w, h)
        indexList = dik.getIndexList()
    }
    
    fun reduce(spx: IntArray, sedges: DoubleArray, dpx: IntArray, dedges: DoubleArray) {
        var j = 0
        for (i in spx.indices) {
            if (indexList.binarySearch(i) >= 0) {
                j++
                continue
            }
            dpx[i - j] = spx[i]
            dedges[i - j] = sedges[i]
        }
    }
}

class VerticalDijkstra(val nodes: Array<Node>, val edges: DoubleArray, val w: Int, val h: Int) {

    val resultPath = arrayListOf<Node>()
    val unprocessList = PriorityQueue<Node>()

    init {
        for (i in 0..w - 1) {
            unprocessList.offer(nodes[i])
        }

        while (true) {
            val node = nextNode()
            if (node.distance == -1.0) {
                break
            }
            val neighbors = getNeighbors(node)
            for (neighbor in neighbors) {
                val weight = getWeight(node, neighbor)
                if (node.distance + weight < neighbor.distance) {
                    neighbor.distance = node.distance + weight
                    neighbor.prevIndex = node.index
                    unprocessList.offer(neighbor)
                }
            }
            node.processed = true
        }

        var minDistance = Double.MAX_VALUE
        var minIndex = -1
        for (i in w * h..nodes.lastIndex) {
            if (minDistance > nodes[i].distance) {
                minDistance = nodes[i].distance
                minIndex = i
            }
        }

        var node = nodes[minIndex]
        while (node.prevIndex != -1) {
            resultPath.add(node)
            node = nodes[node.prevIndex]
        }

        resultPath.reverse()
    }

    fun getIndexList(): IntArray {
        val indexList = IntArray(resultPath.size)
        for (i in resultPath.indices) {
            indexList[i] = resultPath[i].index - w
        }
        return indexList
    }

    fun print() {
        println(resultPath)
    }

    fun nextNode(): Node {

        val node = unprocessList.poll()
        if (node == null) {
            val endNode = Node(-1.0, false, -1, -1, -1, -1)
            return  endNode
        }

        return node
    }

    fun getNeighbors(node: Node): ArrayList<Node> {

        val list = arrayListOf<Node>()

        var i = node.index
        var x = node.x
        var y = node.y

        if (y == h) {
            return list
        }

        if (x - 1 >= 0) {
            list.add(nodes.get(i - 1 + w))
        }
        list.add(nodes.get(i + w))
        if (x + 1 < w - 1) {
            list.add(nodes.get(i + 1 + w))
        }

        return list
    }

    fun getWeight(from: Node, to: Node): Double {
        return edges.get(to.index - w)
    }
}


class HorizontalSeam(val edges: DoubleArray, val w: Int, val h: Int) {

    val indexList: IntArray

    init {
        val nodes = Array<Node>((w + 1) * h) { it ->
            val x = it % (w + 1)
            val y = it / (w + 1)
            var distance = Double.MAX_VALUE
            if (x == 0) {
                distance = 0.0
            }
            Node(distance, false, -1, it, x, y)
        }

        val dik = HorizontalDijkstra(nodes, edges, w, h)
        indexList = dik.getIndexList()
    }

    fun reduce(spx: IntArray, sedges: DoubleArray, dpx: IntArray, dedges: DoubleArray) {
        val tspx = transpose(spx, w, h)
        val tsedges = transpose(sedges, w, h)

        val tdpx = IntArray(w * (h - 1))
        val tdedges = DoubleArray(w * (h - 1))

        val tlist = IntArray(indexList.size)
        for (i in indexList.indices) {
            val data = indexList[i]
            tlist[i] = transposeIndex(data, w, h)
        }

        var j = 0
        for (i in tspx.indices) {
            if (tlist.binarySearch(i) >= 0) {
                j++
                continue
            }
            tdpx[i - j] = tspx[i]
            tdedges[i - j] = tsedges[i]
        }

        transpose(tdpx, dpx, h - 1, w)
        transpose(tdedges, dedges, h - 1, w)
    }

    fun transposeIndex(i: Int, w: Int, h:Int): Int {
        val x = i % w
        val y = i / w
        val j = x * h + y
        return j
    }

    fun transpose(spx: IntArray, w: Int, h: Int): IntArray {
        val tpx = IntArray(w * h)

        for (i in spx.indices) {
            tpx[transposeIndex(i, w, h)] = spx[i]
        }
        return tpx
    }

    fun transpose(spx: DoubleArray, w: Int, h: Int): DoubleArray {
        val tpx = DoubleArray(w * h)

        for (i in spx.indices) {
            tpx[transposeIndex(i, w, h)] = spx[i]
        }
        return tpx
    }

    fun transpose(spx: IntArray, dpx: IntArray, w: Int, h: Int) {
        for (i in spx.indices) {
            dpx[transposeIndex(i, w, h)] = spx[i]
        }
    }

    fun transpose(spx: DoubleArray, dpx: DoubleArray, w: Int, h: Int) {
        for (i in spx.indices) {
            dpx[transposeIndex(i, w, h)] = spx[i]
        }
    }

}

class HorizontalDijkstra(val nodes: Array<Node>, val edges: DoubleArray, val w: Int, val h: Int) {

    val resultPath = arrayListOf<Node>()
    val unprocessList = PriorityQueue<Node>()

    init {
        for (i in 0..h - 1) {
            unprocessList.offer(nodes[i * (w + 1)])
        }

        while (true) {
            val node = nextNode()
            if (node.distance == -1.0) {
                break
            }
            val neighbors = getNeighbors(node)
            for (neighbor in neighbors) {
                val weight = getWeight(node, neighbor)
                if (node.distance + weight < neighbor.distance) {
                    neighbor.distance = node.distance + weight
                    neighbor.prevIndex = node.index
                    unprocessList.offer(neighbor)
                }
            }
            node.processed = true
        }

        var minDistance = Double.MAX_VALUE
        var minIndex = -1
        for (i in 0..h - 1) {
            val index = i * (w + 1) + w
            if (minDistance > nodes[index].distance) {
                minDistance = nodes[index].distance
                minIndex = index
            }
        }

        var node = nodes[minIndex]
        while (node.prevIndex != -1) {
            resultPath.add(node)
            node = nodes[node.prevIndex]
        }

        resultPath.reverse()
    }

    fun getIndexList(): IntArray {
        val indexList = IntArray(resultPath.size)
        for (i in resultPath.indices) {
            indexList[i] = indexNodeToPx(resultPath[i].index)
        }
        return indexList
    }

    fun print() {
        println(resultPath)
    }

    fun nextNode(): Node {

        val node = unprocessList.poll()
        if (node == null) {
            val endNode = Node(-1.0, false, -1, -1, -1, -1)
            return endNode
        }

        return node
    }

    fun getNeighbors(node: Node): ArrayList<Node> {

        val list = arrayListOf<Node>()

        var i = node.index
        var x = node.x
        var y = node.y

        if (x == w) {
            return list
        }

        if (y - 1 >= 0) {
            list.add(nodes[i + 1 - (w + 1)])
        }

        list.add(nodes[i + 1])

        if (y + 1 < h) {
            list.add(nodes[i + 1 + w + 1])
        }

        return list
    }

    fun getWeight(from: Node, to: Node): Double {
        return edges[indexNodeToPx(to.index)]
    }

    fun indexNodeToPx(i: Int): Int {
        return i - 1 - (i / (w + 1))
    }
}

data class Node(var distance: Double, var processed: Boolean, var prevIndex: Int, var index: Int, var x: Int, var y: Int): Comparable<Node> {

    override fun compareTo(other: Node) = (distance - other.distance).toInt()
}

